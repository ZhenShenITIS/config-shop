package tg.configshop.web.registration;

import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.services.AccountMergeService;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelegramWebLinkService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final AccountMergeService accounts;
    private final RegistrationRateLimiter limits;
    private final SecretKeySpec jwtSigningKey;
    private final Clock clock;

    @Transactional
    public LinkCode issue(UUID webUserId) {
        limits.issueTelegramCode(webUserId);
        UUID accountId = lockWebAccount(webUserId);
        if (hasTelegramProfile(accountId)) throw alreadyLinked();
        var previousHash = jdbc.sql("DELETE FROM web_telegram_links WHERE web_user_id = :user RETURNING code_hash")
                .param("user", webUserId).query(String.class).optional();

        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(300);
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = Integer.toString(RANDOM.nextInt(100000, 1000000));
            String hash = hash(code);
            if (previousHash.filter(hash::equals).isPresent()) continue;
            int inserted = jdbc.sql("""
                    INSERT INTO web_telegram_links (web_user_id, code_hash, expires_at)
                    VALUES (:user, :hash, :expires)
                    ON CONFLICT (code_hash) DO UPDATE
                        SET web_user_id = EXCLUDED.web_user_id, expires_at = EXCLUDED.expires_at
                        WHERE web_telegram_links.expires_at <= :now
                    """).param("user", webUserId).param("hash", hash)
                    .param("now", now.atOffset(ZoneOffset.UTC))
                    .param("expires", expiresAt.atOffset(ZoneOffset.UTC)).update();
            if (inserted == 1) return new LinkCode(code, expiresAt);
        }
        throw new WebAuthException(HttpStatus.SERVICE_UNAVAILABLE, "telegram_link_unavailable",
                "Не удалось создать код. Попробуйте ещё раз.");
    }

    @Transactional
    public UUID redeem(long telegramId, String code) {
        if (!code.matches("[0-9]{6}")) throw invalidCode();
        limits.telegramCode(telegramId);
        String hash = hash(code);
        UUID webUserId = jdbc.sql("""
                SELECT web_user_id FROM web_telegram_links WHERE code_hash = :hash AND expires_at > :now
                """).param("hash", hash).param("now", clock.instant().atOffset(ZoneOffset.UTC))
                .query(UUID.class).optional().orElseThrow(this::invalidCode);

        // Serialize merges so both profiles and their current account IDs are resolved together.
        jdbc.sql("SELECT pg_advisory_xact_lock(840025007)").query((rs, row) -> true).single();
        UUID webAccount = lockWebAccount(webUserId);
        UUID telegramAccount = jdbc.sql("SELECT account_id FROM bot_users WHERE telegram_id = :telegram")
                .param("telegram", telegramId).query(UUID.class).optional()
                .orElseThrow(() -> new WebAuthException(HttpStatus.BAD_REQUEST, "telegram_not_registered",
                        "Сначала зарегистрируйтесь в боте через /start."));
        int consumed = jdbc.sql("""
                DELETE FROM web_telegram_links
                WHERE web_user_id = :user AND code_hash = :hash AND expires_at > :now
                """).param("user", webUserId).param("hash", hash)
                .param("now", clock.instant().atOffset(ZoneOffset.UTC)).update();
        if (consumed == 0) throw invalidCode();
        if (!webAccount.equals(telegramAccount) && (hasTelegramProfile(webAccount) || hasWebProfile(telegramAccount))) {
            throw alreadyLinked();
        }
        return webAccount.equals(telegramAccount) ? webAccount : accounts.merge(telegramAccount, webAccount);
    }

    private UUID lockWebAccount(UUID userId) {
        return jdbc.sql("""
                SELECT account_id FROM web_users WHERE id = :user AND email_verified_at IS NOT NULL FOR UPDATE
                """).param("user", userId).query(UUID.class).optional()
                .orElseThrow(() -> new WebAuthException(HttpStatus.UNAUTHORIZED, "unauthorized", "Войдите на сайт."));
    }

    private boolean hasTelegramProfile(UUID accountId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM bot_users WHERE account_id = :account)")
                .param("account", accountId).query(Boolean.class).single();
    }

    private boolean hasWebProfile(UUID accountId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM web_users WHERE account_id = :account)")
                .param("account", accountId).query(Boolean.class).single();
    }

    private String hash(String code) {
        return Hashing.hmacSha256(jwtSigningKey.getEncoded())
                .hashString("telegram-link:" + code, StandardCharsets.UTF_8).toString();
    }

    private WebAuthException invalidCode() {
        return new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_telegram_code",
                "Код неверный или срок его действия истёк. Возьмите новый код на сайте.");
    }

    private WebAuthException alreadyLinked() {
        return new WebAuthException(HttpStatus.CONFLICT, "telegram_already_linked",
                "Один из профилей уже привязан к другому аккаунту.");
    }

    public record LinkCode(String code, Instant expiresAt) { }
}
