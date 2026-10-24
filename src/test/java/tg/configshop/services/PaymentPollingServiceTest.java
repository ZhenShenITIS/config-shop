package tg.configshop.services;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import tg.configshop.config.SpringConfig;
import tg.configshop.constants.PaymentResult;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository.PaymentSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentPollingServiceTest {
    @Test void springSchedulerUsesConfiguredIntervalWithoutUserAction() throws Exception {
        var repository = mock(PlategaPaymentRepository.class);
        var payments = mock(PaymentService.class);
        var candidate = mock(PaymentSnapshot.class);
        UUID owner = UUID.randomUUID();
        when(candidate.getId()).thenReturn(UUID.randomUUID());
        when(candidate.getAccountId()).thenReturn(owner);
        when(candidate.getProviderPaymentId()).thenReturn("scheduled-payment");
        when(candidate.getCreatedAt()).thenReturn(Instant.now());
        when(repository.findPending(any(), any())).thenReturn(List.of(candidate));
        when(repository.findPendingAfter(any(), any(), any(), any())).thenReturn(List.of());
        var checked = new CountDownLatch(2);
        when(payments.checkPayment("scheduled-payment", owner)).thenAnswer(invocation -> {
            checked.countDown();
            return PaymentResult.PROCESSING;
        });

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "polling-test", Map.of("payments.polling.interval", "20ms")));
            context.registerBean(PlategaPaymentRepository.class, () -> repository);
            context.registerBean(PaymentService.class, () -> payments);
            context.register(SpringConfig.class, PaymentPollingService.class);
            context.refresh();
            assertTrue(checked.await(5, TimeUnit.SECONDS), "Scheduled polling must repeat at the configured interval");
            verify(payments, atLeast(2)).checkPayment("scheduled-payment", owner);
        }
    }
}
