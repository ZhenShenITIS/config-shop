package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.repositories.SubscriptionChangeRepository;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionChangeWorker {
    private final SubscriptionChangeRepository changes;
    private final SubscriptionChangeService service;

    @Scheduled(fixedDelayString = "${subscriptions.changes.retry-interval:10s}")
    public void retryPending() {
        var due = changes.findDue(SubscriptionChangeStatus.RETRYABLE, Instant.now(), PageRequest.of(0, 20));
        for (var change : due) {
            try {
                service.retry(change.getId(), change.getSubscriptionId());
            } catch (RuntimeException exception) {
                log.error("Could not recover subscription change {}", change.getId(), exception);
            }
        }
    }
}
