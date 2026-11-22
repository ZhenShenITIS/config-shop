package tg.configshop.web.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import tg.configshop.alerting.AlertService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebAuthMailServiceTest {
    private final AlertService alerts = mock(AlertService.class);

    @Test
    void missingSmtpConfigurationReturnsServiceUnavailable() {
        var service = new WebAuthMailService(new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class),
                alerts, "noreply@example.com", "https://example.com/confirm", "");
        var failure = assertThrows(WebAuthException.class,
                () -> service.send(UUID.randomUUID(), "user@example.com", "token"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatus());
    }

    @Test
    void rejectsMissingOrUnsafeLinkConfigurationWithoutSendingMail() {
        var beans = new StaticListableBeanFactory();
        var sender = mock(JavaMailSender.class);
        beans.addBean("sender", sender);
        for (String url : new String[]{"", "javascript:alert(1)", "https://example.com/confirm#old"}) {
            var service = new WebAuthMailService(beans.getBeanProvider(JavaMailSender.class),
                    alerts, "noreply@example.com", url, "");
            assertThrows(WebAuthException.class, () -> service.send(UUID.randomUUID(), "user@example.com", "token"));
        }
        verifyNoInteractions(sender);
    }

    @Test
    void reportsMissingResetUrlToAlertService() {
        var sender = mock(JavaMailSender.class);
        var beans = new StaticListableBeanFactory();
        beans.addBean("sender", sender);
        var service = new WebAuthMailService(beans.getBeanProvider(JavaMailSender.class),
                alerts, "noreply@example.com", "https://example.com/confirm", "");
        UUID webUserId = UUID.randomUUID();

        service.sendPasswordReset(webUserId, "user@example.com", "token");

        verifyNoInteractions(sender);
        verify(alerts).alertPasswordResetDeliveryFailure(webUserId);
    }

    @Test
    void reportsSmtpFailureToAlertService() {
        var sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP failed")).when(sender).send(any(SimpleMailMessage.class));
        var beans = new StaticListableBeanFactory();
        beans.addBean("sender", sender);
        var service = new WebAuthMailService(beans.getBeanProvider(JavaMailSender.class),
                alerts, "noreply@example.com", "https://example.com/confirm", "https://example.com/reset");
        UUID webUserId = UUID.randomUUID();

        service.sendPasswordReset(webUserId, "user@example.com", "token");

        verify(alerts).alertPasswordResetDeliveryFailure(webUserId);
    }
}
