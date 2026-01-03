package tg.configshop.services;

import tg.configshop.model.BotUser;

import java.util.List;

public interface ReferralService {
    void createReferral (Long referrerId, Long referralId);
    void createReferralCode (Long userId);
    int getAllReferralCount (Long userId);
    int getActiveReferralCount (Long userId);
    long getAllProfit (Long userId);
    String getReferralPromoCode (Long userId);
    int referralPercentage (Long userId);
    List<BotUser> getAllReferrals (Long userId);

}
