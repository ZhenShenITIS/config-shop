package tg.configshop.web.registration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.UUID;

@Service
@Slf4j
public class EmailConfirmationMailService {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private final String confirmationUrl;

    public EmailConfirmationMailService(ObjectProvider<JavaMailSender> senders,
            @Value("${web.registration.mail-from}") String from,
            @Value("${web.registration.confirmation-url}") String confirmationUrl) {
        this.senders = senders;
        this.from = from;
        this.confirmationUrl = confirmationUrl;
    }

    public void send(UUID webUserId, String email, String token) {
        var sender = senders.getIfAvailable();
        if (sender == null || from.isBlank() || !validConfirmationUrl()) {
            log.error("Email confirmation delivery is not configured for web user {}", webUserId);
            throw deliveryFailure();
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Подтверждение почты");
        message.setText("Вы зарегистрировались в сервисе. Подтвердите почту по ссылке:\n"
                + confirmationUrl + "#token=" + token
                + "\n\nСсылка действует 24 часа. Если вы не регистрировались, проигнорируйте письмо.");
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("Email confirmation delivery failed for web user {}", webUserId);
            throw deliveryFailure();
        }
    }

    private boolean validConfirmationUrl() {
        try {
            var uri = URI.create(confirmationUrl);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getFragment() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private WebRegistrationException deliveryFailure() {
        return new WebRegistrationException(HttpStatus.SERVICE_UNAVAILABLE, "confirmation_delivery_failed",
                "Не удалось отправить письмо. Запросите повторную отправку позже.");
    }
}
