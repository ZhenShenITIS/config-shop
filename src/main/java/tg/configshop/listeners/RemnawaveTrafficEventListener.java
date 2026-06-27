package tg.configshop.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tg.configshop.events.TrafficPaidEvent;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemnawaveTrafficEventListener {
    private static final long BYTES_IN_GIGABYTE = 1024L * 1024 * 1024;

    private final RemnawaveClient remnawaveClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTrafficPaid(TrafficPaidEvent event) {
        RemnawaveUserResponse remnawaveUser = remnawaveClient.getUser(event.remnawaveUuid());
        long currentTrafficLimitBytes = remnawaveUser.trafficLimitBytes() == null ? 0L : remnawaveUser.trafficLimitBytes();
        long newTrafficLimitBytes = currentTrafficLimitBytes + ((long) event.trafficGb() * BYTES_IN_GIGABYTE);

        remnawaveClient.updateTrafficLimit(event.remnawaveUuid(), newTrafficLimitBytes);
        log.info("Updated Remnawave traffic limit for user {} by {} GB: {} -> {} bytes",
                event.remnawaveUuid(), event.trafficGb(), currentTrafficLimitBytes, newTrafficLimitBytes);
    }
}
