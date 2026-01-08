package tg.configshop.external_api.pay;

import tg.configshop.external_api.pay.constants.PaymentStatus;
import tg.configshop.external_api.pay.dto.CreatePaymentRequest;
import tg.configshop.external_api.pay.dto.CreatePaymentResponse;

public interface PlategaClient {
    CreatePaymentResponse createPayment(CreatePaymentRequest createPaymentRequest);
    PaymentStatus updateStatus(String paymentId);

}
