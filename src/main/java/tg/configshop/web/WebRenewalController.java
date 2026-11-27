package tg.configshop.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.dto.RenewalOperation;
import tg.configshop.dto.RenewalOptions;
import tg.configshop.dto.RenewalTerms;
import tg.configshop.exceptions.subscription.SubscriptionAccessException;
import tg.configshop.exceptions.subscription.SubscriptionChangeConflictException;
import tg.configshop.exceptions.subscription.SubscriptionChangeInProgressException;
import tg.configshop.services.SubscriptionChangeService;
import tg.configshop.web.registration.WebAuthException;
import tg.configshop.web.security.WebPrincipal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/me/subscriptions/{id}")
public class WebRenewalController {
    private final SubscriptionChangeService renewals;
    private final String supportUrl;

    public WebRenewalController(SubscriptionChangeService renewals, @Value("${SUPPORT_USERNAME:}") String supportUsername) {
        this.renewals = renewals;
        this.supportUrl = supportUsername.matches("[a-zA-Z0-9_]+") ? "https://t.me/" + supportUsername : null;
    }

    @GetMapping("/renewal-options")
    public RenewalOptions options(@AuthenticationPrincipal WebPrincipal user, @PathVariable UUID id) {
        try {
            return renewals.renewalOptions(user.accountId(), id);
        } catch (SubscriptionAccessException e) {
            throw notFound();
        } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
            throw new WebAuthException(HttpStatus.SERVICE_UNAVAILABLE, "renewal_options_unavailable",
                    "Условия продления недоступны. Попробуйте ещё раз.");
        }
    }

    @PostMapping("/renewals")
    public OperationResponse confirm(@AuthenticationPrincipal WebPrincipal user, @PathVariable UUID id,
                                     @Valid @RequestBody ConfirmRequest request) {
        try {
            var terms = new RenewalTerms(request.days().intValueExact(), request.deviceLimit().intValueExact(),
                    request.price().longValueExact(), request.currentExpiresAt());
            return response(renewals.confirmRenewal(request.id(), user.accountId(), id, terms));
        } catch (SubscriptionAccessException e) {
            throw notFound();
        } catch (SubscriptionChangeConflictException e) {
            throw new WebAuthException(HttpStatus.CONFLICT, "renewal_conflict",
                    "Эта попытка уже связана с другими условиями продления.");
        } catch (SubscriptionChangeInProgressException e) {
            throw new WebAuthException(HttpStatus.CONFLICT, "subscription_change_in_progress",
                    "Для этой подписки уже есть незавершённая операция.");
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_renewal", "Проверьте условия продления.");
        }
    }

    @GetMapping("/renewals/{operationId}")
    public OperationResponse status(@AuthenticationPrincipal WebPrincipal user, @PathVariable UUID id,
                                    @PathVariable UUID operationId) {
        return renewals.renewalStatus(operationId, user.accountId(), id).map(this::response)
                .orElseThrow(WebRenewalController::notFound);
    }

    private OperationResponse response(RenewalOperation operation) {
        return new OperationResponse(operation.id(), operation.price(), operation.status(), operation.charged(),
                operation.expiresAt(), operation.status() == SubscriptionChangeStatus.MANUAL_REVIEW ? supportUrl : null);
    }

    private static WebAuthException notFound() {
        return new WebAuthException(HttpStatus.NOT_FOUND, "renewal_not_found", "Подписка или продление не найдены.");
    }

    public record ConfirmRequest(@NotNull UUID id, @NotNull BigDecimal days, @NotNull BigDecimal deviceLimit,
                                 @NotNull BigDecimal price, @NotNull Instant currentExpiresAt) {}

    public record OperationResponse(UUID id, long price, SubscriptionChangeStatus status, boolean charged,
                                    Instant expiresAt, String supportUrl) {}
}
