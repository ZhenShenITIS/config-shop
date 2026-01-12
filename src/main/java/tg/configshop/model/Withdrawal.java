package tg.configshop.model;


import io.hypersistence.tsid.TSID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.configshop.constants.WithdrawalStatus;
import tg.configshop.constants.WithdrawalType;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "withdrawals")
public class Withdrawal {
    @Id
    @Builder.Default
    private Long id = TSID.fast().toLong();
    @ManyToOne
    private BotUser botUser;
    @Builder.Default
    private Instant createdAt = Instant.now();
    private Long amount;
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private WithdrawalStatus status = WithdrawalStatus.IN_PROGRESS;
    @Enumerated(EnumType.STRING)
    // CARD, CRYPTO
    private WithdrawalType type;
    private String requisites;

    public String getPublicId() {
        return TSID.from(id).toString();
    }
}
