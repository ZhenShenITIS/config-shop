package tg.configshop.listeners;

import tg.configshop.services.BalanceService;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;
import tg.configshop.external_api.pay.constants.PaymentStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.events.PaymentConfirmedEvent;
import tg.configshop.events.ReferralRewardEvent;
import tg.configshop.model.Account;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.ReferralService;
import tg.configshop.repositories.AccountRepository;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentConfirmedListener {

    private final ReferralService referralService;
    private final AccountRepository accounts;
    private final TopUpRepository topUpRepository;
    private final BalanceService balanceService;
    private final PlategaPaymentRepository payments;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_REFERRAL_LEVELS = 3;

    @EventListener
    @Transactional
    public void handle(PaymentConfirmedEvent event) {
        var payment = payments.findInternalByIdWithLock(event.paymentId()).orElseThrow();
        if (payment.getStatus() != PaymentStatus.CONFIRMED)
            throw new IllegalStateException("Referral source payment is not confirmed");
        java.util.UUID buyerAccountId = event.accountId();
        long purchaseAmount = event.amount();

        Account buyer = accounts.findById(buyerAccountId).orElseThrow();

        if (!buyer.getId().equals(payment.getAccount().getId())
                || payment.getAmount().longValueExact() != purchaseAmount)
            throw new IllegalArgumentException("Referral source payment mismatch");

        java.util.UUID currentAccountId = buyerAccountId;

        for (int level = 1; level <= MAX_REFERRAL_LEVELS; level++) {
            Optional<java.util.UUID> referrerIdOpt = referralService.getReferrerId(currentAccountId);

            if (referrerIdOpt.isEmpty()) {
                break;
            }

            java.util.UUID referrerId = referrerIdOpt.get();
            Account referrer = accounts.findById(referrerId).orElseThrow();

            double percentage = getPercentageForLevel(referrer, level);

            if (percentage > 0) {
                long reward = (long) (purchaseAmount * (percentage / 100.0));

                if (reward > 0) {
                    if (topUpRepository.existsBySourcePayment_IdAndAccount_IdAndReferralLevel(event.paymentId(), referrer.getId(), (short) level)) {
                        currentAccountId = referrerId;
                        continue;
                    }
                    createAndSaveTopUp(referrer, buyer, reward, event.paymentId(), (short) level);

                    eventPublisher.publishEvent(new ReferralRewardEvent(
                            referrerId,
                            buyerAccountId,
                            reward,
                            level,
                            purchaseAmount,
                            percentage
                    ));
                }
            }
            currentAccountId = referrerId;
        }
    }

    private void createAndSaveTopUp(Account beneficiary, Account sourceUser, long amount, java.util.UUID paymentId, short level) {
        TopUp topUp = TopUp.builder()
                .account(beneficiary)
                .value(amount)
                .topUpSource(TopUpSource.REFERRAL)
                .referral(sourceUser)
                .sourcePayment(payments.getReferenceById(paymentId))
                .referralLevel(level)
                .build();

        balanceService.credit(topUp);
    }

    private double getPercentageForLevel(Account user, int level) {
        return switch (level) {
            case 1 -> user.getReferralPercentage1lvl();
            case 2 -> user.getReferralPercentage2lvl();
            case 3 -> user.getReferralPercentage3lvl();
            default -> 0.0;
        };
    }
}
