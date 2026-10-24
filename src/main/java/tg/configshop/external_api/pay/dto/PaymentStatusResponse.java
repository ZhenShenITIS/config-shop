package tg.configshop.external_api.pay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tg.configshop.external_api.pay.constants.PaymentStatus;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentStatusResponse(
        String id,
        PaymentStatus status,
        Details paymentDetails
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Details(BigDecimal amount, String currency) {}
}
