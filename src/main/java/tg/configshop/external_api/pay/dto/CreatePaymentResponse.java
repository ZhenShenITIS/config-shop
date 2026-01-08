package tg.configshop.external_api.pay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tg.configshop.external_api.pay.constants.PaymentMethod;
import tg.configshop.external_api.pay.constants.PaymentStatus;

public record CreatePaymentResponse (
        String transactionId,
        PaymentMethod paymentMethod,
        String redirect,
        @JsonProperty("return")
        String returnUrl,
        String paymentDetails,
        PaymentStatus status,
        String expiresIn,
        String merchantId,
        Double usdtRate,
        Double cryptoAmount

) {
}
