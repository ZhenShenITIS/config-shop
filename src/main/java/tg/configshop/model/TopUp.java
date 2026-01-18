package tg.configshop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.configshop.constants.TopUpSource;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "top_ups")
public class TopUp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long value;
    // REFERRAL/EXTERNAL/PROMO_CODE/ADMIN
    @Enumerated(EnumType.STRING)
    private TopUpSource topUpSource;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @ManyToOne
    private BotUser botUser;
    private String externalId;
    @ManyToOne
    private BotUser referral;

}
