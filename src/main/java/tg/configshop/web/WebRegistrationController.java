package tg.configshop.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.configshop.web.dto.ConfirmEmailRequest;
import tg.configshop.web.dto.ConfirmEmailResponse;
import tg.configshop.web.dto.MessageResponse;
import tg.configshop.web.dto.RegisterRequest;
import tg.configshop.web.dto.ResendConfirmationRequest;
import tg.configshop.web.registration.RegistrationRateLimiter;
import tg.configshop.web.registration.WebRegistrationService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class WebRegistrationController {
    private final WebRegistrationService registration;
    private final RegistrationRateLimiter rateLimiter;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        rateLimiter.register(request.getRemoteAddr());
        registration.register(body);
        return new MessageResponse("Аккаунт создан. Подтвердите почту по ссылке из письма.");
    }

    @PostMapping("/confirm-email")
    public ConfirmEmailResponse confirm(@Valid @RequestBody ConfirmEmailRequest body, HttpServletRequest request) {
        rateLimiter.confirm(request.getRemoteAddr());
        return registration.confirm(body.token());
    }

    @PostMapping("/resend-confirmation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resend(@Valid @RequestBody ResendConfirmationRequest body, HttpServletRequest request) {
        rateLimiter.resend(request.getRemoteAddr());
        registration.resend(body.email());
        return new MessageResponse("Если адрес ожидает подтверждения, письмо отправлено.");
    }
}
