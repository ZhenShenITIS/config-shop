package tg.configshop.external_api.pay.dto;

public record PaymentDetails (
        Double amount,
        String currency
) {
}
