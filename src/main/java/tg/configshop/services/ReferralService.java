package tg.configshop.services;

import org.springframework.data.domain.Page;
import tg.configshop.dto.ReferralWithProfit;
import tg.configshop.dto.ReferralWithProfitAndLevel;
import tg.configshop.model.BotUser;
import tg.configshop.model.Referral;

import java.util.List;
import java.util.Optional;

public interface ReferralService {
    void createReferral (Long referrerId, Long referralId);
    void createReferralCode (Long userId);
    int getAllReferralCount (Long userId);
    int getActiveReferralCount (Long userId);
    long getAllProfit (Long userId);
    String getReferralPromoCode (Long userId);
    Optional<Long> getReferrerId (Long userId);
    Long getAvailableSumToWithdraw(Long userId);
    Page<ReferralWithProfitAndLevel> getReferralsWithProfitAndLevel (Long userId, int pageNumber);


}
