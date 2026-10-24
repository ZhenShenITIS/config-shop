package tg.configshop.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WithdrawalCreatedEvent extends ApplicationEvent {
    private final java.util.UUID withdrawalId;

    public WithdrawalCreatedEvent(Object source, java.util.UUID withdrawalId) {
        super(source);
        this.withdrawalId = withdrawalId;
    }
}
