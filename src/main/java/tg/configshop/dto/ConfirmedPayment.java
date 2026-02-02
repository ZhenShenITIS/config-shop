package tg.configshop.dto;

public record ConfirmedPayment(
        Long userId,
        Long amount,
        String paymentId
) {
}
