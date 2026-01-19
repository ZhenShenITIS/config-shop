package tg.configshop.dto;

import tg.configshop.constants.OperationType;
import tg.configshop.constants.PurchaseType;
import tg.configshop.constants.TopUpSource;
import tg.configshop.constants.WithdrawalStatus;
import tg.configshop.constants.WithdrawalType;

import java.time.Instant;

public interface OperationView {
    Long getAmount();
    Instant getDate();
    OperationType getOperationType();

    TopUpSource getTopUpSource();

    PurchaseType getPurchaseType();
    Integer getDeviceCount();
    Integer getDurationDays();

    WithdrawalStatus getWithdrawalStatus();
    WithdrawalType getWithdrawalType();
}
