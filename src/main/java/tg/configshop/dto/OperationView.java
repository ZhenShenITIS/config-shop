package tg.configshop.dto;

import tg.configshop.constants.OperationType;
import tg.configshop.constants.PurchaseType;
import tg.configshop.constants.TopUpSource;

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
    OperationType getOperationType();

    TopUpSource getTopUpSource();

    PurchaseType getPurchaseType();
    Integer getDeviceCount();
    Integer getDurationDays();
}
