package tg.configshop.repositories;

import tg.configshop.constants.DialogStageName;
import tg.configshop.dto.PaymentContext;
import tg.configshop.dto.WithdrawalContext;

public interface UserStateRepository {
    DialogStageName get (Long userId);
    void put (Long userId, DialogStageName dialogStageName);

    PaymentContext getPaymentContext (Long userId);
    void putPaymentContext (Long userId, PaymentContext paymentContext);
    void clearPaymentContext (Long userId);

    WithdrawalContext getWithdrawalContext (Long userId);
    void putWithdrawalContext(Long userId, WithdrawalContext paymentContext);
    void clearWithdrawalContext (Long userId);
}
