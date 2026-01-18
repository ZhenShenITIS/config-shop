package tg.configshop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.configshop.constants.PurchaseType;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "purchases")
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long paidAmount;
    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;
    @ManyToOne
    @JoinColumn(name = "bot_user_id")
    private BotUser botUser;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    @Enumerated(EnumType.STRING)
    // DEVICE, SUBSCRIPTION
    private PurchaseType purchaseType = PurchaseType.SUBSCRIPTION;
    private Integer deviceCount;
}
