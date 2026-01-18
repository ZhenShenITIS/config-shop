package tg.configshop.dto;

import tg.configshop.constants.OperationType;

import java.time.Instant;

/*
public interface ReferralView {
    Long getUserId();
    String getFirstName();
    Instant getExpireAt();
    Long getProfit();
    Instant getReferredAt();
    Integer getLvl();
}
 */
public interface OperationView {
    Long getAmount();
    Instant getDate();
    String getDescription();
    OperationType getOperationType();
}
