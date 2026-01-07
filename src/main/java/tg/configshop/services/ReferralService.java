package tg.configshop.services;

import org.springframework.data.domain.Page;
import tg.configshop.dto.ReferralWithProfit;
import tg.configshop.model.BotUser;

import java.util.List;

public interface ReferralService {
    void createReferral (Long referrerId, Long referralId);
    void createReferralCode (Long userId);
    int getAllReferralCount (Long userId);
    int getActiveReferralCount (Long userId);
    long getAllProfit (Long userId);
    String getReferralPromoCode (Long userId);
    List<BotUser> getAllReferrals (Long userId);
    Page<ReferralWithProfit> getReferralsWithProfit (Long userId, int pageNumber);


}
