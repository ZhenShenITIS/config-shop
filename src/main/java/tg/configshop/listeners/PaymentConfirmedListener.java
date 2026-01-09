package tg.configshop.listeners;

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
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_REFERRAL_LEVELS = 3;

    @EventListener
    @Transactional
    public void handle(PaymentConfirmedEvent event) {
        long buyerId = event.getUserId();
        long purchaseAmount = event.getAmount();

        BotUser buyer = userService.getUser(buyerId);

        if (buyer == null) return;

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
                    userService.addToBalance(referrerId, reward);

                    createAndSaveTopUp(referrer, buyer, reward);

                    eventPublisher.publishEvent(new ReferralRewardEvent(
                            referrerId,
                            buyerId,
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

    private void createAndSaveTopUp(BotUser beneficiary, BotUser sourceUser, long amount) {
        TopUp topUp = TopUp.builder()
                .botUser(beneficiary)
                .value(amount)
                .topUpSource(TopUpSource.REFERRAL)
                .referral(sourceUser)
                .build();

        topUpRepository.save(topUp);
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
