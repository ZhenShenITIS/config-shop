package tg.configshop.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tg.configshop.web.dto.LoginRequest;
import tg.configshop.web.dto.MessageResponse;
import tg.configshop.web.dto.ResendConfirmationRequest;
import tg.configshop.web.dto.ResetPasswordRequest;
import tg.configshop.web.registration.RegistrationRateLimiter;
import tg.configshop.web.registration.TelegramWebLinkService;
import tg.configshop.web.registration.WebAuthException;
import tg.configshop.web.security.WebAuthenticationService;
import tg.configshop.web.security.WebAuthenticationService.Session;
import tg.configshop.web.security.WebPrincipal;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class WebAuthenticationController {
    private static final String REFRESH_COOKIE = "__Host-config_shop_refresh";
    private static final String CSRF_HEADER = "X-CSRF-Protection";

    private final WebAuthenticationService auth;
    private final RegistrationRateLimiter rateLimiter;
    private final TelegramWebLinkService telegramLinks;

    @PostMapping("/link-telegram")
    public TelegramWebLinkService.LinkCode linkTelegram(@AuthenticationPrincipal WebPrincipal user) {
        return telegramLinks.issue(user.webUserId());
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AccessTokenResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        rateLimiter.login(request.getRemoteAddr());
        return sessionResponse(auth.login(body.email(), body.password()), response);
    }

    @PostMapping("/refresh")
    public AccessTokenResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrf, HttpServletResponse response) {
        requireCsrfHeader(csrf);
        Session session = auth.refresh(token);
        if (session == null) {
            setCookie(response, "", Duration.ZERO);
            throw new WebAuthException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token",
                    "Сеанс завершён. Войдите снова.");
        }
        return sessionResponse(session, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrf, HttpServletResponse response) {
        requireCsrfHeader(csrf);
        auth.logout(token);
        setCookie(response, "", Duration.ZERO);
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody ResendConfirmationRequest body,
            HttpServletRequest request) {
        rateLimiter.forgotPassword(request.getRemoteAddr());
        auth.requestPasswordReset(body.email());
        return new MessageResponse("Если аккаунт с этим адресом существует, мы отправим письмо для сброса пароля.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        rateLimiter.resetPassword(request.getRemoteAddr());
        auth.resetPassword(body.token(), body.password());
    }

    private void requireCsrfHeader(String csrf) {
        // An HTML form cannot send this header. Cross-origin requests with it need a CORS preflight.
        if (!"1".equals(csrf)) {
            throw new WebAuthException(HttpStatus.FORBIDDEN, "forbidden", "Запрос заблокирован.");
        }
    }

    private AccessTokenResponse sessionResponse(Session session, HttpServletResponse response) {
        setCookie(response, session.refreshToken(), session.refreshMaxAge());
        return new AccessTokenResponse(session.accessToken(), "Bearer", session.expiresIn());
    }

    private void setCookie(HttpServletResponse response, String token, Duration ttl) {
        var cookie = ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true).secure(true)
                .sameSite("Lax").path("/").maxAge(ttl).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public record AccessTokenResponse(String accessToken, String tokenType, long expiresIn) { }
}
