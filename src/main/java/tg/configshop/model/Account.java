package tg.configshop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.FetchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
import java.math.BigDecimal;


@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.SMALLINT)
    @Builder.Default
    private Integer referralPercentage1lvl = 35;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.SMALLINT)
    @Builder.Default
    private Integer referralPercentage2lvl = 20;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.SMALLINT)
    @Builder.Default
    private Integer referralPercentage3lvl = 10;
    @Column(precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal subscriptionPriceRate = BigDecimal.ONE;
    @Column(precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal trafficPackageRate = BigDecimal.ONE;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "account", fetch = FetchType.EAGER)
    @Builder.Default
    private java.util.List<RemnawaveSubscription> subscriptions = new java.util.ArrayList<>();
}
