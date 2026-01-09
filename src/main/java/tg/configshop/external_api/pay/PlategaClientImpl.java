package tg.configshop.external_api.pay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tg.configshop.external_api.pay.constants.PaymentStatus;
import tg.configshop.external_api.pay.dto.CreatePaymentRequest;
import tg.configshop.external_api.pay.dto.CreatePaymentResponse;
import tg.configshop.external_api.pay.dto.PaymentStatusResponse;

@Component
@RequiredArgsConstructor
public class PlategaClientImpl implements PlategaClient {
    private final RestClient plategaRestClient;
    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest createPaymentRequest) {
        return plategaRestClient.post()
                .uri("/transaction/process")
                .body(createPaymentRequest)
                .retrieve()
                .body(CreatePaymentResponse.class);
    }

    @Override
    public PaymentStatus updateStatus(String paymentId) {
        PaymentStatusResponse response = plategaRestClient.get()
                .uri("/transaction/{paymentId}", paymentId)
                .retrieve()
                .body(PaymentStatusResponse.class);
        return response.status();
    }
}
