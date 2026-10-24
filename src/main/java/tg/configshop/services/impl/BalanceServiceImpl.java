package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.services.BalanceService;
import tg.configshop.model.*;
import tg.configshop.constants.*;
import tg.configshop.repositories.*;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class BalanceServiceImpl implements BalanceService {
    private final AccountRepository accounts;
    private final BalanceLogRepository log;
    private final TopUpRepository topUps;
    private final PurchaseRepository purchases;
    private final WithdrawalRepository withdrawals;
    @Override public long balance(UUID accountId) { return log.balance(accountId); }
    private void lock(Account account) { accounts.findByIdWithLock(account.getId()).orElseThrow(); }
    private void checkFunds(Account account, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative debit");
        if (balance(account.getId()) < amount) throw new InsufficientBalanceException();
    }
    @Override @Transactional
    public void credit(TopUp topUp) {
        lock(topUp.getAccount());
        if (topUp.getId() != null && log.existsByTopUp_Id(topUp.getId())) return;
        if (topUp.getValue() <= 0 && topUp.getTopUpSource() != TopUpSource.ADMIN)
            throw new IllegalArgumentException("Non-positive credit");
        topUps.saveAndFlush(topUp);
        BalanceLogType type = switch (topUp.getTopUpSource()) {
            case ADMIN -> BalanceLogType.ADMIN;
            case REFERRAL -> BalanceLogType.TOP_UP_REFERRAL;
            case EXTERNAL -> BalanceLogType.TOP_UP_EXTERNAL;
            case PROMO_CODE -> BalanceLogType.TOP_UP_PROMO_CODE;
        };
        log.saveAndFlush(BalanceLog.builder().account(topUp.getAccount()).amount(topUp.getValue())
            .type(type).topUp(topUp).build());
    }
    @Override @Transactional
    public void purchase(Purchase purchase) {
        lock(purchase.getAccount());
        if (!purchase.getAccount().getId().equals(purchase.getRemnawaveSubscription().getAccount().getId()))
            throw new IllegalArgumentException("Subscription belongs to another account");
        if (purchase.getId() != null && log.existsByPurchase_Id(purchase.getId())) return;
        checkFunds(purchase.getAccount(), purchase.getPaidAmount());
        purchases.saveAndFlush(purchase);
        log.saveAndFlush(BalanceLog.builder().account(purchase.getAccount()).amount(-purchase.getPaidAmount())
            .type(BalanceLogType.valueOf(purchase.getPurchaseType().name() + "_PURCHASE")).purchase(purchase).build());
    }
    @Override @Transactional
    public void withdrawal(Withdrawal withdrawal, boolean refund) {
        lock(withdrawal.getAccount());
        BalanceLogType type = refund ? BalanceLogType.WITHDRAWAL_REJECT : BalanceLogType.WITHDRAWAL;
        if (withdrawal.getId() != null && log.existsByWithdrawal_IdAndType(withdrawal.getId(), type)) return;
        if (withdrawal.getAmount() <= 0) throw new IllegalArgumentException("Non-positive withdrawal");
        if (refund && (withdrawal.getStatus() != WithdrawalStatus.REJECTED || withdrawal.getId() == null
                || !log.existsByWithdrawal_IdAndType(withdrawal.getId(), BalanceLogType.WITHDRAWAL)))
            throw new IllegalStateException("Refund requires a rejected and debited withdrawal");
        if (!refund) checkFunds(withdrawal.getAccount(), withdrawal.getAmount());
        withdrawals.saveAndFlush(withdrawal);
        log.saveAndFlush(BalanceLog.builder().account(withdrawal.getAccount())
            .amount(refund ? withdrawal.getAmount() : -withdrawal.getAmount()).type(type).withdrawal(withdrawal).build());
    }
}
