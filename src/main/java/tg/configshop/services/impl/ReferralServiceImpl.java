package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;
import tg.configshop.model.Referral;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.PromoCodeRepository;
import tg.configshop.repositories.ReferralRepository;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.ReferralService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReferralServiceImpl implements ReferralService {
    private final ReferralRepository referralRepository;
    private final BotUserRepository botUserRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final TopUpRepository topUpRepository;

    private final String PROMO_CODE_PREFIX = "REF_";
    private final Long REFERRAL_PROMO_CODE_BASE_AMOUNT = 100L;

    @Override
    @Transactional
    public void createReferral(Long referrerId, Long referralId) {
        Optional<Referral> existingReferral = referralRepository.findByReferral_Id(referralId);

        if (existingReferral.isPresent()) {
            Referral referral = existingReferral.get();
            referral.setReferrer(botUserRepository.getReferenceById(referrerId));
            referral.setCreatedAt(Instant.now());
            referralRepository.save(referral);
        } else {

            referralRepository.save(Referral
                    .builder()
                    .referrer(botUserRepository.getReferenceById(referrerId))
                    .referral(botUserRepository.getReferenceById(referralId))
                    .build());
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
    public List<BotUser> getAllReferrals(Long userId) {
        return referralRepository.findAllByReferrer(botUserRepository.getReferenceById(userId));
    }
}
