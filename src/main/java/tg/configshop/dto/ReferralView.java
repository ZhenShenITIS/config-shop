package tg.configshop.dto;

import java.time.Instant;

public interface ReferralView {
    Long getUserId();
    String getFirstName();
    Instant getExpireAt();
    Long getProfit();
    Instant getReferredAt();
    Integer getLvl();
}