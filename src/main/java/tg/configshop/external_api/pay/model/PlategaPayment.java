package tg.configshop.external_api.pay.model;

import tg.configshop.external_api.pay.converter.PaymentMethodConverter;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import tg.configshop.model.Account;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.configshop.external_api.pay.constants.PaymentMethod;
import tg.configshop.external_api.pay.constants.PaymentStatus;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "platega_payments")
public class PlategaPayment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "provider_payment_id", unique = true, nullable = false)
    private String transactionId;
    @ManyToOne(optional = false)
    private Account account;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
    @Convert(converter = PaymentMethodConverter.class)
    private PaymentMethod paymentMethod;
    private String redirect;
    @Column(precision = 20, scale = 2)
    private BigDecimal amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

}
