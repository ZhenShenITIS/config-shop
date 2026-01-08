package tg.configshop.external_api.pay;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tg.configshop.external_api.pay.constants.PaymentStatus;
import tg.configshop.external_api.pay.dto.CreatePaymentRequest;
import tg.configshop.external_api.pay.dto.CreatePaymentResponse;
import tg.configshop.external_api.pay.dto.PaymentStatusResponse;
import tg.configshop.external_api.pay.model.PlategaPayment;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;

@Component
@RequiredArgsConstructor
public class PlategaClientImpl implements PlategaClient {
    private final RestClient plategaRestClient;
    private final PlategaPaymentRepository paymentRepository;
    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest createPaymentRequest) {
        CreatePaymentResponse createPaymentResponse = plategaRestClient.post()
                .uri("/transaction/process")
                .body(createPaymentRequest)
                .retrieve()
                .body(CreatePaymentResponse.class);

        PlategaPayment plategaPayment = PlategaPayment
                .builder()
                .paymentDetails(createPaymentResponse.paymentDetails())
                .status(createPaymentResponse.status())
                .paymentMethod(createPaymentResponse.paymentMethod())
                .cryptoAmount(createPaymentResponse.cryptoAmount())
                .redirect(createPaymentResponse.redirect())
                .returnUrl(createPaymentResponse.returnUrl())
                .transactionId(createPaymentResponse.transactionId())
                .usdtRate(createPaymentResponse.usdtRate())
                .build();
        paymentRepository.save(plategaPayment);
        return createPaymentResponse;
    }

    @Override
    public PaymentStatus updateStatus(String paymentId) {
        PlategaPayment plategaPayment = paymentRepository.findById(paymentId).orElseThrow();
        PaymentStatusResponse response = plategaRestClient.get()
                .uri("/transaction/{paymentId}", paymentId)
                .retrieve()
                .body(PaymentStatusResponse.class);
        plategaPayment.setStatus(response.status());
        paymentRepository.save(plategaPayment);
        return response.status();
    }
}
