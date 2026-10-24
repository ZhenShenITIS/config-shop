package tg.configshop.dto;

import java.time.Instant;

public interface ReferralView {
    Long getUserId();
    String getFirstName();
    Long getProfit();
    Instant getReferredAt();
    Integer getLvl();
}
