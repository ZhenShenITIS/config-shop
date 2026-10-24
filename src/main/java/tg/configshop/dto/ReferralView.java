package tg.configshop.dto;
import java.time.Instant;
import java.util.UUID;
public interface ReferralView {
    UUID getAccountId();
    Long getProfit();
    Instant getReferredAt();
    Integer getLvl();
}
