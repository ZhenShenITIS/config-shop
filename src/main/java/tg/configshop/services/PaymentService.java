package tg.configshop.services;

import tg.configshop.constants.PaymentResult;
import tg.configshop.external_api.pay.model.PlategaPayment;

public interface PaymentService {

    PaymentResult checkPayment(String paymentId, Long userId);

    PlategaPayment createPlategaPayment (Long amount, int paymentMethodInt, Long userId);
}
