package tg.configshop.web.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailConfirmationMailServiceTest {
    @Test
    void missingSmtpConfigurationReturnsServiceUnavailable() {
        var service = new EmailConfirmationMailService(new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class),
                "noreply@example.com", "https://example.com/confirm");
        var failure = assertThrows(WebRegistrationException.class,
                () -> service.send(UUID.randomUUID(), "user@example.com", "token"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatus());
    }

    @Test
    void rejectsMissingOrUnsafeLinkConfigurationWithoutSendingMail() {
        var beans = new StaticListableBeanFactory();
        var sender = mock(JavaMailSender.class);
        beans.addBean("sender", sender);
        for (String url : new String[]{"", "javascript:alert(1)", "https://example.com/confirm#old"}) {
            var service = new EmailConfirmationMailService(beans.getBeanProvider(JavaMailSender.class), "noreply@example.com", url);
            assertThrows(WebRegistrationException.class, () -> service.send(UUID.randomUUID(), "user@example.com", "token"));
        }
        verifyNoInteractions(sender);
    }
}
