package tg.configshop.services.impl;

import tg.configshop.services.BalanceService;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.model.Account;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.exceptions.promocode.CyclicReferralException;
import tg.configshop.exceptions.promocode.InvalidSymbolsPromoException;
import tg.configshop.exceptions.promocode.PromoCodeAlreadyExistException;
import tg.configshop.exceptions.promocode.PromoCodeAlreadyUsedException;
import tg.configshop.exceptions.promocode.PromoCodeEndedException;
import tg.configshop.exceptions.promocode.PromoCodeNotFoundException;
import tg.configshop.exceptions.promocode.ReferralPromoCodeAlreadyUsedException;
import tg.configshop.exceptions.promocode.SelfReferralException;
import tg.configshop.exceptions.promocode.TooManyReferralPromoException;
import tg.configshop.model.PromoCode;
import tg.configshop.model.PromoCodeUse;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.PromoCodeRepository;
import tg.configshop.repositories.PromoCodeUseRepository;
import tg.configshop.repositories.ReferralRepository;
import tg.configshop.services.PromoCodeService;
import tg.configshop.services.ReferralService;
import tg.configshop.util.StringUtil;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PromoCodeServiceImpl implements PromoCodeService {
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeUseRepository promoCodeUseRepository;
    private final ReferralService referralService;
    private final BalanceService balanceService;
    private final AccountRepository accountRepository;
    private final ReferralRepository referralRepository;

    private final int MAX_USES_REFERRAL_CODE = 10000;
    private final long AMOUNT_REFERRAL_CODE = 100;

    @Override
    @Transactional
    public void activatePromoCode(String code, java.util.UUID accountId) {

        PromoCode promoCode = getPromoCode(code.toUpperCase().trim());

        Account user = accountRepository.findByIdWithLock(accountId).orElseThrow();

        checkReferralConditions(promoCode, user);

        checkBasicConditions(promoCode, user);

        usePromoCode(promoCode, user);

        setReferralIfPromoIsReferral(promoCode, user);

    }

    @Override
    @Transactional
    public void createPromoCode(PromoCode promoCode) throws PromoCodeAlreadyExistException {
        if (promoCodeRepository.findByCode(promoCode.getCode()).isPresent()) {
            throw new PromoCodeAlreadyExistException();
        }
        promoCodeRepository.save(promoCode);
    }

    @Override
    @Transactional
    public void createReferralPromoCode(String code, java.util.UUID accountId) throws TooManyReferralPromoException, PromoCodeAlreadyExistException, InvalidSymbolsPromoException {
        String upperCode = code.toUpperCase().trim();

        StringUtil.validateCodeFormat(upperCode);

        if (promoCodeRepository.findByCode(upperCode).isPresent()) {
            throw new PromoCodeAlreadyExistException();
        }

        if (promoCodeRepository.findCodesByReferrer(accountRepository.findByIdWithLock(accountId).orElseThrow()).size() >= 10) {
            throw new TooManyReferralPromoException();
        }

        promoCodeRepository.save(PromoCode
                .builder()
                        .isReferral(true)
                        .referrer(accountRepository.findByIdWithLock(accountId).orElseThrow())
                        .maxUses(MAX_USES_REFERRAL_CODE)
                        .amount(AMOUNT_REFERRAL_CODE)
                        .code(upperCode)
                .build());
    }

    private PromoCode getPromoCode (String code) {
        return promoCodeRepository.findByCodeWithLock(code)
                .orElseThrow(() -> new PromoCodeNotFoundException());
    }

    private void checkReferralConditions(PromoCode promoCode, Account account) {
        if (promoCode.getIsReferral()) {
            Account promoOwner = promoCode.getReferrer();

            if (promoOwner != null && promoOwner.getId().equals(account.getId())) {
                throw new SelfReferralException();
            }

            if (promoCodeUseRepository.existsByAccountAndPromoCode_IsReferralTrue(account)) {
                throw new ReferralPromoCodeAlreadyUsedException();
            }

            if (promoOwner != null) {
                boolean isCycle = referralRepository.isAccountAncestorOf(account.getId(), promoOwner.getId());
                if (isCycle) {
                    throw new CyclicReferralException();
                }
            }
        }
    }

    private void checkBasicConditions(PromoCode promoCode, Account user) {
        if (promoCode.getMaxUses() != null && promoCode.getCurrentUses() >= promoCode.getMaxUses()) {
            throw new PromoCodeEndedException();
        }
        if (promoCodeUseRepository.existsByPromoCodeAndAccount(promoCode, user)) {
            throw new PromoCodeAlreadyUsedException();
        }
    }

    private void setReferralIfPromoIsReferral (PromoCode promoCode, Account account) {
        if (promoCode.getIsReferral() && promoCode.getReferrer() != null) {
            referralService.createReferral(promoCode.getReferrer().getId(), account.getId());
        }
    }

    private void usePromoCode (PromoCode promoCode, Account user) {


        createTopUp(promoCode, user);

        increaseUseCountOfPromoCode(promoCode, user);

        createPromoCodeUsage(promoCode, user);

    }

    private void createTopUp (PromoCode promoCode, Account user) {
        TopUp topUp = TopUp.builder()
                .account(user)
                .value(promoCode.getAmount())
                .topUpSource(TopUpSource.PROMO_CODE)
                .promoCode(promoCode)
                .createdAt(Instant.now())
                .build();
        balanceService.credit(topUp);
    }

    private void increaseUseCountOfPromoCode (PromoCode promoCode, Account user) {
        promoCode.setCurrentUses(promoCode.getCurrentUses() + 1);
        promoCodeRepository.save(promoCode);
    }

    private void createPromoCodeUsage (PromoCode promoCode, Account user) {
        PromoCodeUse usage = PromoCodeUse.builder()
                .promoCode(promoCode)
                .account(user)
                .createdAt(Instant.now())
                .build();
        promoCodeUseRepository.save(usage);
    }


}
