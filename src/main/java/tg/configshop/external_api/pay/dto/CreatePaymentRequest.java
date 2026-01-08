package tg.configshop.external_api.pay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreatePaymentRequest (
        int paymentMethod,
        PaymentDetails paymentDetails,
        String description,
        @JsonProperty("return")
        String returnUrl,
        String failedUrl,
        String payload
) {
}
