package tg.configshop.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PaymentConfirmedEvent extends ApplicationEvent {
    private final Long userId;
    private final Long amount;
    private final java.util.UUID paymentId;

    public PaymentConfirmedEvent(Object source, Long userId, Long amount, java.util.UUID paymentId) {
        super(source);
        this.userId = userId;
        this.amount = amount;
        this.paymentId = paymentId;
    }
}
