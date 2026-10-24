package tg.configshop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;
import tg.configshop.constants.PurchaseType;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "purchases")
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private Long paidAmount;
    @ManyToOne
    private Account account;
    @ManyToOne
    private RemnawaveSubscription remnawaveSubscription;
    @Column(precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal accountPriceRate = BigDecimal.ONE;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private PurchaseType purchaseType = PurchaseType.SUBSCRIPTION;
    private Integer subDays;
    private Integer subDevices;
    private Integer deviceCount;
    private Integer trafficGb;

}
