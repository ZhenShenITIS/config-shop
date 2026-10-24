package tg.configshop.dto;
import java.util.UUID;
import java.time.Instant;
import tg.configshop.constants.WithdrawalType;
import tg.configshop.constants.WithdrawalStatus;
public record WithdrawalDetails(UUID id, UUID accountId, long amount, WithdrawalType type,
                                WithdrawalStatus status, String requisites, Instant createdAt) {
    public String getPublicId() { return id.toString(); }
    public UUID getId() { return id; }
    public long getAmount() { return amount; }
    public WithdrawalType getType() { return type; }
    public WithdrawalStatus getStatus() { return status; }
    public String getRequisites() { return requisites; }
}
