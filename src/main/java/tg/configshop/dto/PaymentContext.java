package tg.configshop.dto;

import tg.configshop.external_api.pay.constants.PaymentMethod;

public record PaymentContext(
        PaymentMethod paymentMethod,
        long lastCheckTime
) {
    public PaymentContext (PaymentMethod paymentMethod) {
        this(paymentMethod, 0);
    }
}
