package tg.configshop.services;
import tg.configshop.constants.PaymentResult;
import tg.configshop.dto.PaymentView;
import tg.configshop.dto.PaymentLimits;
import java.util.UUID;
public interface PaymentService {
    PaymentResult checkPayment(String providerPaymentId, UUID accountId);
    PaymentView createPlategaPayment(Long amount, int paymentMethod, UUID accountId, String returnUrl, String failedUrl);
    PaymentLimits limits();
}
