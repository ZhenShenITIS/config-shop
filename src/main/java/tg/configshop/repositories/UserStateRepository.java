package tg.configshop.repositories;

import tg.configshop.constants.DialogStageName;
import tg.configshop.dto.PaymentContext;

public interface UserStateRepository {
    DialogStageName get (Long userId);
    void put (Long userId, DialogStageName dialogStageName);

    PaymentContext getPaymentContext (Long userId);
    void putPaymentContext (Long userId, PaymentContext paymentContext);
}
