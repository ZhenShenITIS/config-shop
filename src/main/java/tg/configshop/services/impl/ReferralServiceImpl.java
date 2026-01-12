package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.dto.ReferralView;
import tg.configshop.dto.ReferralWithProfit;
import tg.configshop.dto.ReferralWithProfitAndLevel;
import tg.configshop.events.ReferralCreatedEvent;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;
import tg.configshop.model.Referral;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.PromoCodeRepository;
import tg.configshop.repositories.ReferralRepository;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.repositories.WithdrawalRepository;
import tg.configshop.services.ReferralService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// TODO decompose god-service
public class ReferralServiceImpl implements ReferralService {
    private final ReferralRepository referralRepository;
    private final BotUserRepository botUserRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final TopUpRepository topUpRepository;
    private final ApplicationEventPublisher eventPublisher;


    private final WithdrawalRepository withdrawalRepository;

    private final String PROMO_CODE_PREFIX = "REF_";
    private final long REFERRAL_PROMO_CODE_BASE_AMOUNT = 100L;
    private final int REFERRAL_PAGE_SIZE = 15;

    @Override
    @Transactional
    public void createReferral(Long referrerId, Long referralId) {
        Optional<Referral> existingReferral = referralRepository.findByReferral_Id(referralId);

        if (existingReferral.isEmpty()) {
            referralRepository.save(Referral
                    .builder()
                    .referrer(botUserRepository.getReferenceById(referrerId))
                    .referral(botUserRepository.getReferenceById(referralId))
                    .build());
            eventPublisher.publishEvent(new ReferralCreatedEvent(this, referrerId, referralId));
        }
    }

    @Override
    public void createReferralCode(Long userId) {
        promoCodeRepository.save(PromoCode
                .builder()
                .code(PROMO_CODE_PREFIX + userId)
                .isReferral(true)
                .amount(REFERRAL_PROMO_CODE_BASE_AMOUNT)
                .maxUses(1000)
                .referrer(botUserRepository.getReferenceById(userId))
                .build()
        );
    }

    @Override
    public int getAllReferralCount(Long userId) {
        return referralRepository.countReferralsByReferrer_Id(userId);
    }

    @Override
    public int getActiveReferralCount(Long userId) {
        return referralRepository.countActiveReferrals(userId, Instant.now());
    }

    @Override
    public long getAllProfit(Long userId) {
        return topUpRepository.getSumByUserIdAndSource(userId, TopUpSource.REFERRAL);
    }

    @Override
    public String getReferralPromoCode(Long userId) {
        return promoCodeRepository.findAllByReferrer(botUserRepository.getReferenceById(userId)).get(0).getCode();
    }

    @Override
    public Long getAvailableSumToWithdraw(Long userId) {
        Long totalReferralEarnings = topUpRepository.getSumByUserIdAndSource(userId, TopUpSource.REFERRAL);
        Long totalWithdrawnOrPending = withdrawalRepository.sumConsumedReferralBalance(userId);
        long theoreticalReferralBalance = Math.max(0, totalReferralEarnings - totalWithdrawnOrPending);

        Long currentWalletBalance = botUserRepository.findById(userId)
                .map(BotUser::getBalance)
                .orElse(0L);
        return Math.min(theoreticalReferralBalance, currentWalletBalance);
    }

    @Override
    public Page<ReferralWithProfitAndLevel> getReferralsWithProfitAndLevel(Long userId, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, REFERRAL_PAGE_SIZE);

        Page<ReferralView> viewPage = referralRepository.findReferralsProjection(userId, pageable);

        return viewPage.map(view -> {
            BotUser tempUser = BotUser.builder()
                    .id(view.getUserId())
                    .firstName(view.getFirstName())
                    .expireAt(view.getExpireAt())
                    .build();

            return new ReferralWithProfitAndLevel(
                    tempUser,
                    view.getProfit(),
                    view.getReferredAt(),
                    view.getLvl()
            );
        });
    }

    @Override
    public Optional<Long> getReferrerId(Long userId) {
        Referral referral = referralRepository.findByReferral_Id(userId).orElse(null);
        if (referral != null) {
            return Optional.of(referral.getReferrer().getId());
        }
        return Optional.empty();
    }
}
