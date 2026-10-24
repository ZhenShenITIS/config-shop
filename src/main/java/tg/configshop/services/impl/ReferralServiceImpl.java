package tg.configshop.services.impl;

import tg.configshop.services.BalanceService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.dto.ReferralView;
import tg.configshop.dto.ReferralWithProfitAndLevel;
import tg.configshop.events.ReferralCreatedEvent;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;
import tg.configshop.model.Referral;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.PromoCodeRepository;
import tg.configshop.repositories.PromoCodeUseRepository;
import tg.configshop.repositories.ReferralRepository;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.repositories.WithdrawalRepository;
import tg.configshop.services.ReferralService;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
// TODO decompose god-service
public class ReferralServiceImpl implements ReferralService {
    private final ReferralRepository referralRepository;
    private final BotUserRepository botUserRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final TopUpRepository topUpRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PromoCodeUseRepository promoCodeUseRepository;


    private final WithdrawalRepository withdrawalRepository;
    private final BalanceService balanceService;

    private final String PROMO_CODE_PREFIX = "REF_";
    private final long REFERRAL_PROMO_CODE_BASE_AMOUNT = 100L;
    private final int REFERRAL_PAGE_SIZE = 15;

    @Override
    @Transactional
    public void createReferral(Long referrerId, Long referralId) {
        Optional<Referral> existingReferral = referralRepository.findByReferral_Id(botUserRepository.findById(referralId).orElseThrow().getAccount().getId());

        if (existingReferral.isEmpty()) {
            referralRepository.save(Referral
                    .builder()
                    .referrer(botUserRepository.getReferenceById(referrerId).getAccount())
                    .referral(botUserRepository.getReferenceById(referralId).getAccount())
                    .build());
            eventPublisher.publishEvent(new ReferralCreatedEvent(this, referrerId, referralId));
        }
    }

    @Override
    @Transactional
    public void createReferralCode(Long userId) {
        if (promoCodeRepository.findByCode(PROMO_CODE_PREFIX + userId).isPresent()) return;
        promoCodeRepository.save(PromoCode
                .builder()
                .code(PROMO_CODE_PREFIX + userId)
                .isReferral(true)
                .amount(REFERRAL_PROMO_CODE_BASE_AMOUNT)
                .maxUses(1000)
                .referrer(botUserRepository.getReferenceById(userId).getAccount())
                .build()
        );
    }

    @Override
    public int getAllReferralCount(Long userId) {
        return referralRepository.countReferralsByReferrer_Id(botUserRepository.findById(userId).orElseThrow().getAccount().getId());
    }

    @Override
    public long getAllProfit(Long userId) {
        return topUpRepository.getSumByUserIdAndSource(botUserRepository.findById(userId).orElseThrow().getAccount().getId(), TopUpSource.REFERRAL);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getReferralPromoCodes(Long userId) {
        return promoCodeRepository.findCodesByReferrer(botUserRepository.getReferenceById(userId).getAccount());
    }

    @Override
    public Long getAvailableSumToWithdraw(Long userId) {
        Long totalReferralEarnings = topUpRepository.getSumByUserIdAndSource(botUserRepository.findById(userId).orElseThrow().getAccount().getId(), TopUpSource.REFERRAL);
        Long totalWithdrawnOrPending = withdrawalRepository.sumConsumedReferralBalance(botUserRepository.findById(userId).orElseThrow().getAccount().getId());
        long theoreticalReferralBalance = Math.max(0, totalReferralEarnings - totalWithdrawnOrPending);

        long currentWalletBalance = balanceService.balance(botUserRepository.findById(userId).orElseThrow().getAccount().getId());
        return Math.max(0, Math.min(theoreticalReferralBalance, currentWalletBalance));
    }

    @Override
    public Page<ReferralWithProfitAndLevel> getReferralsWithProfitAndLevel(Long userId, int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber, REFERRAL_PAGE_SIZE);

        Page<ReferralView> viewPage = referralRepository.findReferralsProjection(botUserRepository.findById(userId).orElseThrow().getAccount().getId(), pageable);

        return viewPage.map(view -> {
            BotUser tempUser = BotUser.builder()
                    .telegramId(view.getUserId())
                    .name(view.getFirstName())
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
    public Optional<PromoCode> getUsedPromo(Long userId) {
        return promoCodeUseRepository.findReferralPromoUse(botUserRepository.findById(userId).orElseThrow().getAccount().getId());
    }

    @Override
    public Optional<Long> getReferrerId(Long userId) {
        Referral referral = referralRepository.findByReferral_Id(botUserRepository.findById(userId).orElseThrow().getAccount().getId()).orElse(null);
        if (referral != null) {
            return botUserRepository.findFirstByAccount_IdOrderByTelegramIdAsc(referral.getReferrer().getId()).map(BotUser::getId);
        }
        return Optional.empty();
    }
}
