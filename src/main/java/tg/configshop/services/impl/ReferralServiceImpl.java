package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.dto.ReferralWithProfitAndLevel;
import tg.configshop.events.ReferralCreatedEvent;
import tg.configshop.exceptions.promocode.*;
import tg.configshop.model.PromoCode;
import tg.configshop.model.Referral;
import tg.configshop.repositories.*;
import tg.configshop.services.BalanceService;
import tg.configshop.services.ReferralService;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReferralServiceImpl implements ReferralService {
    private final ReferralRepository referralRepository;
    private final AccountRepository accounts;
    private final PromoCodeRepository promoCodeRepository;
    private final TopUpRepository topUpRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PromoCodeUseRepository promoCodeUseRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final BalanceService balanceService;

    @Override @Transactional
    public void createReferral(UUID referrerAccountId, UUID referralAccountId) {
        var referral = accounts.findByIdWithLock(referralAccountId).orElseThrow();
        var referrer = accounts.findById(referrerAccountId).orElseThrow();
        if (referrerAccountId.equals(referralAccountId)) throw new SelfReferralException();
        if (referralRepository.isAccountAncestorOf(referralAccountId, referrerAccountId)) throw new CyclicReferralException();
        if (referralRepository.findByReferral_Id(referralAccountId).isEmpty()) {
            referralRepository.save(Referral.builder().referrer(referrer).referral(referral).build());
            eventPublisher.publishEvent(new ReferralCreatedEvent(referrerAccountId, referralAccountId));
        }
    }
    @Override @Transactional
    public void createReferralCode(UUID accountId) { createReferralCode(accountId, "REF_" + accountId.toString().replace("-", "")); }

    @Override @Transactional
    public void createReferralCode(UUID accountId, String code) {
        var account = accounts.findByIdWithLock(accountId).orElseThrow();
        var existing = promoCodeRepository.findByCode(code);
        if (existing.isPresent()) {
            if (!Boolean.TRUE.equals(existing.get().getIsReferral()) || existing.get().getReferrer() == null
                    || !accountId.equals(existing.get().getReferrer().getId())) throw new PromoCodeAlreadyExistException();
            return;
        }
        promoCodeRepository.save(PromoCode.builder().code(code).isReferral(true).amount(100L)
                .maxUses(1000).referrer(account).build());
    }
    @Override public int getAllReferralCount(UUID accountId) { return referralRepository.countReferralsByReferrer_Id(accountId); }
    @Override public long getAllProfit(UUID accountId) { return topUpRepository.getSumByAccountIdAndSource(accountId, TopUpSource.REFERRAL); }
    @Override @Transactional(readOnly = true)
    public List<String> getReferralPromoCodes(UUID accountId) {
        return promoCodeRepository.findCodesByReferrer(accounts.findById(accountId).orElseThrow());
    }
    @Override public Long getAvailableSumToWithdraw(UUID accountId) {
        long earned = getAllProfit(accountId);
        long consumed = withdrawalRepository.sumConsumedReferralBalance(accountId);
        return Math.max(0, Math.min(Math.max(0, earned - consumed), balanceService.balance(accountId)));
    }
    @Override @Transactional(readOnly = true)
    public Page<ReferralWithProfitAndLevel> getReferralsWithProfitAndLevel(UUID accountId, int pageNumber, int pageSize) {
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("Invalid page size");
        return referralRepository.findReferralsProjection(accountId, PageRequest.of(pageNumber, pageSize))
                .map(v -> new ReferralWithProfitAndLevel(v.getAccountId(), v.getProfit(), v.getReferredAt(), v.getLvl()));
    }
    @Override @Transactional(readOnly = true)
    public Optional<String> getUsedPromo(UUID accountId) { return promoCodeUseRepository.findReferralPromoUse(accountId).map(PromoCode::getCode); }
    @Override @Transactional(readOnly = true)
    public Optional<UUID> getReferrerId(UUID accountId) {
        return referralRepository.findByReferral_Id(accountId).map(r -> r.getReferrer().getId());
    }
}
