package tg.configshop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;
import tg.configshop.constants.TopUpSource;
import tg.configshop.external_api.pay.model.PlategaPayment;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "top_ups")
public class TopUp {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private Long value;
    @Enumerated(EnumType.STRING)
    private TopUpSource topUpSource;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @ManyToOne
    private Account account;
    @ManyToOne
    @JoinColumn(name = "platega_id")
    private PlategaPayment platega;
    @ManyToOne
    private PromoCode promoCode;
    @ManyToOne
    private Account referral;
    @ManyToOne
    private PlategaPayment sourcePayment;
    private Short referralLevel;
    private String reason;

}
