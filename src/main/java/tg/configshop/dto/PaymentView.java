package tg.configshop.dto;
import java.util.UUID;
import tg.configshop.external_api.pay.constants.PaymentStatus;
public record PaymentView(UUID id, String transactionId, long amount, PaymentStatus status, String redirect) {}
