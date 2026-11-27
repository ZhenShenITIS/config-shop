package tg.configshop.services;

import tg.configshop.constants.PaymentResult;
import tg.configshop.dto.PaymentLimits;
import tg.configshop.dto.PaymentView;
import tg.configshop.dto.TopUpView;

import java.util.Optional;
import java.util.UUID;

public interface PaymentService {
    PaymentResult checkPayment(String providerPaymentId, UUID accountId);
    PaymentView createPlategaPayment(Long amount, int paymentMethod, UUID accountId, String returnUrl, String failedUrl);
    PaymentView createTopUp(UUID requestId, Long amount, int paymentMethod, UUID accountId,
                            String buyerEmail, String returnUrl, String failedUrl);
    Optional<TopUpView> getTopUp(UUID requestId, UUID accountId);
    PaymentLimits limits();
}
