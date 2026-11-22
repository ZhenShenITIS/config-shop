package tg.configshop.web.registration;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class WebAuthException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public WebAuthException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    static WebAuthException emailTaken() {
        return new WebAuthException(HttpStatus.CONFLICT, "email_already_registered", "Этот email уже зарегистрирован.");
    }

    static WebAuthException invalidConfirmationToken() {
        return new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_confirmation_token",
                "Ссылка подтверждения недействительна или срок её действия истёк.");
    }

    static WebAuthException confirmationCooldown() {
        return new WebAuthException(HttpStatus.TOO_MANY_REQUESTS, "confirmation_cooldown",
                "Повторное письмо можно запросить через минуту.");
    }
}
