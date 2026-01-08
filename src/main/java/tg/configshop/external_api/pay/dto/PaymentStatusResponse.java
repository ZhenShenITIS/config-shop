package tg.configshop.external_api.pay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tg.configshop.external_api.pay.constants.PaymentStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentStatusResponse(
        String id,
        PaymentStatus status
) {

}
