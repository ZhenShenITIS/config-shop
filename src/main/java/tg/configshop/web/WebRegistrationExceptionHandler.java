package tg.configshop.web;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tg.configshop.web.registration.WebRegistrationException;

@RestControllerAdvice(assignableTypes = WebRegistrationController.class)
public class WebRegistrationExceptionHandler {
    @ExceptionHandler(WebRegistrationException.class)
    public ResponseEntity<ErrorResponse> registrationError(WebRegistrationException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getError(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", "Проверьте данные запроса."));
    }

    public record ErrorResponse(String error, String message) { }
}
