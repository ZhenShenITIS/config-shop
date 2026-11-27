package tg.configshop.web;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tg.configshop.web.registration.WebAuthException;

@RestControllerAdvice(assignableTypes = {WebRegistrationController.class, WebAuthenticationController.class,
        WebDashboardController.class, WebTopUpController.class})
public class WebAuthExceptionHandler {
    @ExceptionHandler(WebAuthException.class)
    public ResponseEntity<ErrorResponse> authError(WebAuthException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getError(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", "Проверьте данные запроса."));
    }

    public record ErrorResponse(String error, String message) { }
}
