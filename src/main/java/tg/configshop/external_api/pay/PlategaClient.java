package tg.configshop.external_api.pay;

import tg.configshop.external_api.pay.dto.CreatePaymentRequest;
import tg.configshop.external_api.pay.dto.CreatePaymentResponse;
import tg.configshop.external_api.pay.dto.PaymentStatusResponse;

public interface PlategaClient {
    CreatePaymentResponse createPayment(CreatePaymentRequest createPaymentRequest);
    PaymentStatusResponse getPayment(String paymentId);

}
