package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.exceptions.promocode.CyclicReferralException;
import tg.configshop.exceptions.promocode.PromoCodeAlreadyUsedException;
import tg.configshop.exceptions.promocode.PromoCodeEndedException;
import tg.configshop.exceptions.promocode.PromoCodeNotFoundException;
import tg.configshop.exceptions.promocode.ReferralPromoCodeAlreadyUsedException;
import tg.configshop.exceptions.promocode.SelfReferralException;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;
import tg.configshop.model.PromoCodeUse;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.PromoCodeRepository;
import tg.configshop.repositories.PromoCodeUseRepository;
import tg.configshop.repositories.ReferralRepository;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.PromoCodeService;
import tg.configshop.services.ReferralService;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PromoCodeServiceImpl implements PromoCodeService {
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeUseRepository promoCodeUseRepository;
    private final BotUserRepository botUserRepository;
    private final ReferralService referralService;
    private final TopUpRepository topUpRepository;
    private final ReferralRepository referralRepository;

    @Override
    @Transactional
    public void activatePromoCode(String code, Long userId) {
        PromoCode promoCode = getPromoCode(code);

        BotUser user = getBotUser(userId);

        checkReferralConditions(promoCode, user);

        checkBasicConditions(promoCode, user);

        usePromoCode(promoCode, user);

        setReferralIfPromoIsReferral(promoCode, user);

    }

    @Override
    @Transactional
    public void createPromoCode(PromoCode promoCode) {
        if (promoCodeRepository.findByCode(promoCode.getCode()).isPresent()) {
            throw new RuntimeException("Промокод с таким названием уже существует");
        }
        promoCodeRepository.save(promoCode);
    }

    private PromoCode getPromoCode (String code) {
        return promoCodeRepository.findByCodeWithLock(code)
                .orElseThrow(() -> new PromoCodeNotFoundException());
    }

    private BotUser getBotUser (Long userId) {
        return botUserRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }



    private void checkReferralConditions(PromoCode promoCode, BotUser botUser) {
        if (promoCode.getIsReferral()) {
            BotUser promoOwner = promoCode.getReferrer();

            if (promoOwner != null && promoOwner.getId().equals(botUser.getId())) {
                throw new SelfReferralException();
            }

            if (promoCodeUseRepository.existsByBotUserAndPromoCode_IsReferralTrue(botUser)) {
                throw new ReferralPromoCodeAlreadyUsedException();
            }

            if (promoOwner != null) {
                boolean isCycle = referralRepository.isUserAncestorOf(botUser.getId(), promoOwner.getId());
                if (isCycle) {
                    throw new CyclicReferralException();
                }
            }
        }
    }

    private void checkBasicConditions(PromoCode promoCode, BotUser user) {
        if (promoCode.getMaxUses() != null && promoCode.getCurrentUses() >= promoCode.getMaxUses()) {
            throw new PromoCodeEndedException();
        }
        if (promoCodeUseRepository.existsByPromoCodeAndBotUser(promoCode, user)) {
            throw new PromoCodeAlreadyUsedException();
        }
    }

    private void setReferralIfPromoIsReferral (PromoCode promoCode, BotUser botUser) {
        if (promoCode.getIsReferral() && promoCode.getReferrer() != null) {
            referralService.createReferral(promoCode.getReferrer().getId(), botUser.getId());
        }
    }

    private void usePromoCode (PromoCode promoCode, BotUser user) {

        increaseUserBalance(promoCode, user);

        createTopUp(promoCode, user);

        increaseUseCountOfPromoCode(promoCode, user);

        createPromoCodeUsage(promoCode, user);

    }

    private void increaseUserBalance (PromoCode promoCode, BotUser user) {
        user.setBalance(user.getBalance() + promoCode.getAmount());
        botUserRepository.save(user);
    }

    private void createTopUp (PromoCode promoCode, BotUser user) {
        TopUp topUp = TopUp.builder()
                .botUser(user)
                .value(promoCode.getAmount())
                .topUpSource(TopUpSource.PROMO_CODE)
                .externalId(promoCode.getCode())
                .createdAt(Instant.now())
                .build();
        topUpRepository.save(topUp);
    }

    private void increaseUseCountOfPromoCode (PromoCode promoCode, BotUser user) {
        promoCode.setCurrentUses(promoCode.getCurrentUses() + 1);
        promoCodeRepository.save(promoCode);
    }

    private void createPromoCodeUsage (PromoCode promoCode, BotUser user) {
        PromoCodeUse usage = PromoCodeUse.builder()
                .promoCode(promoCode)
                .botUser(user)
                .createdAt(Instant.now())
                .build();
        promoCodeUseRepository.save(usage);
    }


}

