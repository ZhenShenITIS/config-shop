package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountMergeService {
    private final JdbcClient jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID merge(UUID telegramAccount, UUID webAccount) {
        var ordered = jdbc.sql("""
                SELECT id FROM accounts WHERE id IN (:telegram, :web)
                ORDER BY created_at, id FOR UPDATE
                """).param("telegram", telegramAccount).param("web", webAccount).query(UUID.class).list();
        if (ordered.size() != 2) throw new IllegalStateException("Account to merge no longer exists");
        UUID keep = ordered.getFirst();
        UUID move = ordered.getLast();

        // The related rows must all finish with the same owner. Other transactions still check immediately.
        jdbc.sql("""
                SET CONSTRAINTS v2_purchases_subscription_fk, v2_top_ups_payment_fk,
                    v2_top_ups_referral_payment_fk, v2_balance_log_purchase_fk, v2_balance_log_top_up_fk,
                    v2_balance_log_withdrawal_fk, bot_users_preferred_subscription_fk DEFERRED
                """).update();
        jdbc.sql("""
                UPDATE bot_users SET preferred_subscription_id = (
                    SELECT id FROM remnawave_subscriptions WHERE account_id = :account
                        AND (SELECT count(*) FROM remnawave_subscriptions WHERE account_id = :account) = 1
                )
                WHERE account_id = :account AND preferred_subscription_id IS NULL
                """).param("account", telegramAccount).update();

        moveReferrals(keep, move);
        jdbc.sql("""
                DELETE FROM promo_code_uses AS duplicate USING promo_code_uses AS existing
                WHERE duplicate.account_id = :move AND existing.account_id = :keep
                    AND duplicate.promo_code_id = existing.promo_code_id
                """).param("move", move).param("keep", keep).update();
        jdbc.sql("""
                UPDATE top_ups SET original_reward_account_id = :move
                WHERE account_id = :move AND source_payment_id IS NOT NULL AND original_reward_account_id IS NULL
                """).param("move", move).update();
        for (String table : new String[]{"bot_users", "web_users", "remnawave_subscriptions", "platega_payments",
                "purchases", "top_ups", "withdrawals", "balance_log", "promo_code_uses"}) {
            moveReference(table, "account_id", keep, move);
        }
        moveReference("promo_codes", "referrer_id", keep, move);
        moveReference("top_ups", "referral_id", keep, move);
        moveReference("email_confirmations", "referrer_account_id", keep, move);

        jdbc.sql("DELETE FROM accounts WHERE id = :move").param("move", move).update();
        return keep;
    }

    private void moveReferrals(UUID keep, UUID move) {
        jdbc.sql("LOCK TABLE referrals IN SHARE ROW EXCLUSIVE MODE").update();
        var parents = jdbc.sql("""
                DELETE FROM referrals WHERE referral_id IN (:keep, :move)
                RETURNING id, referrer_id, referral_id, created_at
                """).param("keep", keep).param("move", move)
                .query((rs, row) -> new Referral(rs.getObject("id", UUID.class),
                        rs.getObject("referrer_id", UUID.class), rs.getObject("referral_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class))).list();
        moveReference("referrals", "referrer_id", keep, move);
        var valid = parents.stream().filter(parent -> !parent.referrerId().equals(keep)
                && !parent.referrerId().equals(move) && !wouldCreateCycle(keep, parent.referrerId())).toList();
        valid.stream().filter(parent -> parent.referralId().equals(keep)).findFirst()
                .or(() -> valid.stream().findFirst())
                .ifPresent(parent -> jdbc.sql("""
                        INSERT INTO referrals (id, referrer_id, referral_id, created_at)
                        VALUES (:id, :referrer, :keep, :created)
                        """).param("id", parent.id()).param("referrer", parent.referrerId())
                        .param("keep", keep).param("created", parent.createdAt()).update());
    }

    private boolean wouldCreateCycle(UUID accountId, UUID parentId) {
        return jdbc.sql("""
                WITH RECURSIVE ancestors(id) AS (
                    SELECT CAST(:parent AS uuid)
                    UNION
                    SELECT r.referrer_id FROM referrals r JOIN ancestors a ON a.id = r.referral_id
                )
                SELECT EXISTS (SELECT 1 FROM ancestors WHERE id = :account)
                """).param("parent", parentId).param("account", accountId).query(Boolean.class).single();
    }

    private void moveReference(String table, String column, UUID keep, UUID move) {
        jdbc.sql("UPDATE " + table + " SET " + column + " = :keep WHERE " + column + " = :move")
                .param("keep", keep).param("move", move).update();
    }

    private record Referral(UUID id, UUID referrerId, UUID referralId, OffsetDateTime createdAt) { }
}
