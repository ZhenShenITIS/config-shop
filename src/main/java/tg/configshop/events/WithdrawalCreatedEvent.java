package tg.configshop.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WithdrawalCreatedEvent extends ApplicationEvent {
    private final Long withdrawalId;

    public WithdrawalCreatedEvent(Object source, Long withdrawalId) {
        super(source);
        this.withdrawalId = withdrawalId;
    }
}
