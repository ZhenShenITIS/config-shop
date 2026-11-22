package tg.configshop.web.registration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tg.configshop.alerting.AlertService;
import tg.configshop.web.WebTokens;

import java.util.UUID;

@Service
@Slf4j
public class WebAuthMailService {
    private final ObjectProvider<JavaMailSender> senders;
    private final AlertService alerts;
    private final String from;
    private final String confirmationUrl;
    private final String passwordResetUrl;

    public WebAuthMailService(ObjectProvider<JavaMailSender> senders, AlertService alerts,
            @Value("${web.registration.mail-from}") String from,
            @Value("${web.registration.confirmation-url}") String confirmationUrl,
            @Value("${web.auth.password-reset-url}") String passwordResetUrl) {
        this.senders = senders;
        this.alerts = alerts;
        this.from = from;
        this.confirmationUrl = confirmationUrl;
        this.passwordResetUrl = passwordResetUrl;
    }

    public void send(UUID recipientId, String email, String token) {
        if (!sendLink(recipientId, email, confirmationUrl, "Подтверждение почты",
                "Подтвердите почту по ссылке:\n" + confirmationUrl + "#token=" + token
                + "\n\nСсылка действует 24 часа. Если вы не регистрировались, проигнорируйте письмо.")) {
            throw new WebAuthException(HttpStatus.SERVICE_UNAVAILABLE, "confirmation_delivery_failed",
                    "Не удалось отправить письмо. Запросите повторную отправку позже.");
        }
    }

    @Async
    public void sendPasswordReset(UUID webUserId, String email, String token) {
        if (!sendLink(webUserId, email, passwordResetUrl, "Сброс пароля",
                "Установите новый пароль по ссылке:\n" + passwordResetUrl + "#token=" + token
                + "\n\nСсылка действует 30 минут. Если вы не запрашивали сброс пароля, проигнорируйте письмо.")) {
            alerts.alertPasswordResetDeliveryFailure(webUserId);
        }
    }

    private boolean sendLink(UUID recipientId, String email, String url, String subject, String text) {
        var sender = senders.getIfAvailable();
        if (sender == null || from.isBlank() || !WebTokens.validLinkUrl(url)) {
            log.error("Web auth email delivery is not configured for recipient {}", recipientId);
            return false;
        }
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
            return true;
        } catch (MailException e) {
            log.error("Web auth email delivery failed for recipient {}", recipientId);
            return false;
        }
    }
}
