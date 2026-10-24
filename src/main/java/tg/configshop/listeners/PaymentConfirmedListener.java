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
import tg.configshop.model.BotUser;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.ReferralService;
import tg.configshop.services.UserService;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentConfirmedListener {

    private final ReferralService referralService;
    private final UserService userService;
    private final TopUpRepository topUpRepository;
    private final BalanceService balanceService;
    private final PlategaPaymentRepository payments;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_REFERRAL_LEVELS = 3;

    @EventListener
    @Transactional
    public void handle(PaymentConfirmedEvent event) {
        var payment = payments.findInternalByIdWithLock(event.getPaymentId()).orElseThrow();
        if (payment.getStatus() != PaymentStatus.CONFIRMED)
            throw new IllegalStateException("Referral source payment is not confirmed");
        long buyerId = event.getUserId();
        long purchaseAmount = event.getAmount();

        BotUser buyer = userService.getUser(buyerId);

        if (buyer == null) return;
        if (!buyer.getAccount().getId().equals(payment.getAccount().getId())
                || payment.getAmount().longValueExact() != purchaseAmount)
            throw new IllegalArgumentException("Referral source payment mismatch");

        long currentIteratedUserId = buyerId;

        for (int level = 1; level <= MAX_REFERRAL_LEVELS; level++) {
            Optional<Long> referrerIdOpt = referralService.getReferrerId(currentIteratedUserId);

            if (referrerIdOpt.isEmpty()) {
                break;
            }

            Long referrerId = referrerIdOpt.get();
            BotUser referrer = userService.getUser(referrerId);

            if (referrer == null) {
                break;
            }

            double percentage = getPercentageForLevel(referrer, level);

            if (percentage > 0) {
                long reward = (long) (purchaseAmount * (percentage / 100.0));

                if (reward > 0) {
                    if (topUpRepository.existsBySourcePayment_IdAndAccount_IdAndReferralLevel(event.getPaymentId(), referrer.getAccount().getId(), (short) level)) {
                        currentIteratedUserId = referrerId;
                        continue;
                    }
                    createAndSaveTopUp(referrer, buyer, reward, event.getPaymentId(), (short) level);

                    eventPublisher.publishEvent(new ReferralRewardEvent(
                            referrerId,
                            buyerId,
                            buyer.getFirstName(),
                            buyer.getUsername(),
                            reward,
                            level,
                            purchaseAmount,
                            percentage
                    ));
                }
            }
            currentIteratedUserId = referrerId;
        }
    }

    private void createAndSaveTopUp(BotUser beneficiary, BotUser sourceUser, long amount, java.util.UUID paymentId, short level) {
        TopUp topUp = TopUp.builder()
                .account(beneficiary.getAccount())
                .value(amount)
                .topUpSource(TopUpSource.REFERRAL)
                .referral(sourceUser.getAccount())
                .sourcePayment(payments.getReferenceById(paymentId))
                .referralLevel(level)
                .build();

        balanceService.credit(topUp);
    }

    private double getPercentageForLevel(BotUser user, int level) {
        return switch (level) {
            case 1 -> user.getReferralPercentage1lvl();
            case 2 -> user.getReferralPercentage2lvl();
            case 3 -> user.getReferralPercentage3lvl();
            default -> 0.0;
        };
    }
}
