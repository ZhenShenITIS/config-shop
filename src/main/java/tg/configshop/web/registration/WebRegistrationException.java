package tg.configshop.web.registration;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class WebRegistrationException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    public WebRegistrationException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }
}
