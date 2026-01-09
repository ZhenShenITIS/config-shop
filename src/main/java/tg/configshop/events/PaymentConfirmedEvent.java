package tg.configshop.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
@Getter
public class PaymentConfirmedEvent extends ApplicationEvent {
    private final Long userId;
    private final Long amount;

    public PaymentConfirmedEvent(Object source, Long userId, Long amount) {
        super(source);
        this.userId = userId;
        this.amount = amount;
    }
}
