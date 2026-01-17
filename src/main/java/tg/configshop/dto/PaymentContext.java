package tg.configshop.dto;

import tg.configshop.external_api.pay.constants.PaymentMethod;

public record PaymentContext(
        PaymentMethod paymentMethod
) {
}
