package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payments.polling.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentPollingService {
    private final PlategaPaymentRepository payments;
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payments.polling.interval:30s}")
    public void checkPendingPayments() {
        var createdBefore = Instant.now();
        var page = PageRequest.of(0, 100);
        var pending = payments.findPending(createdBefore, page);
        while (!pending.isEmpty()) {
            for (var payment : pending) {
                try {
                    paymentService.checkPayment(payment.getProviderPaymentId(), payment.getAccountId());
                } catch (Exception e) {
                    log.error("Error checking pending payment {}", payment.getId(), e);
                }
            }
            var last = pending.getLast();
            pending = payments.findPendingAfter(createdBefore, last.getCreatedAt(), last.getId(), page);
        }
    }
}
