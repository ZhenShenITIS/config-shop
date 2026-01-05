package tg.configshop.events;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
@Getter
public class ReferralCreatedEvent extends ApplicationEvent {
    private final Long referrerId;
    private final Long referralId;

    public ReferralCreatedEvent(Object source, Long referrerId, Long referralId) {
        super(source);
        this.referrerId = referrerId;
        this.referralId = referralId;
    }
}
