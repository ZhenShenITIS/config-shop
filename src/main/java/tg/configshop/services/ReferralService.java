package tg.configshop.services;
import org.springframework.data.domain.Page;
import tg.configshop.dto.ReferralWithProfitAndLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ReferralService {
    void createReferral(UUID referrerAccountId, UUID referralAccountId);
    void createReferralCode(UUID accountId);
    void createReferralCode(UUID accountId, String code);
    int getAllReferralCount(UUID accountId);
    long getAllProfit(UUID accountId);
    List<String> getReferralPromoCodes(UUID accountId);
    Optional<UUID> getReferrerId(UUID accountId);
    Long getAvailableSumToWithdraw(UUID accountId);
    Page<ReferralWithProfitAndLevel> getReferralsWithProfitAndLevel(UUID accountId, int pageNumber, int pageSize);
    Optional<String> getUsedPromo(UUID accountId);
}
