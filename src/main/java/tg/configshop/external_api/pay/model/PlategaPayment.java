package tg.configshop.external_api.pay.model;

import jakarta.persistence.Entity;
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
    private String transactionId;
    private PaymentMethod paymentMethod;
    private String redirect;
    private String returnUrl;
    private String paymentDetails;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private Double usdtRate;
    private Double cryptoAmount;

}
