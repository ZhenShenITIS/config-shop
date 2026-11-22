package tg.configshop.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import tg.configshop.exceptions.subscription.SubscriptionAccessException;
import tg.configshop.services.AccountOverviewService;
import tg.configshop.services.ExternalSubscriptionService;
import tg.configshop.web.registration.WebAuthException;
import tg.configshop.web.security.WebPrincipal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class WebDashboardController {
    private final AccountOverviewService accounts;
    private final ExternalSubscriptionService externalSubscriptions;

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal WebPrincipal user) {
        var accountId = user.accountId();
        // A concurrent account merge can invalidate the account ID from the JWT.
        var snapshot = accounts.load(accountId)
                .orElseThrow(() -> new WebAuthException(HttpStatus.UNAUTHORIZED, "unauthorized", "Обновите сеанс."));
        var summaries = snapshot.subscriptions().stream()
                .map(subscription -> new SubscriptionItem(subscription.id())).toList();
        return new DashboardResponse(accountId, user.email(), snapshot.balance(), snapshot.linkedProfiles(), summaries);
    }

    @GetMapping("/subscriptions/{id}")
    public SubscriptionDetailsResponse subscription(@AuthenticationPrincipal WebPrincipal user, @PathVariable UUID id) {
        try {
            var details = externalSubscriptions.getExternalUserWithCryptLinkAndSync(user.accountId(), id, true);
            return new SubscriptionDetailsResponse(id, details.expireAt(), details.isActive(),
                    details.userTraffic().usedTraffic(), details.trafficLimitGigaBytes(), details.subscriptionUrl());
        } catch (SubscriptionAccessException exception) {
            throw new WebAuthException(HttpStatus.NOT_FOUND, "subscription_not_found", "Подписка не найдена.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Could not load connection details for subscription {} ({})", id,
                    exception.getClass().getSimpleName());
            throw new WebAuthException(HttpStatus.SERVICE_UNAVAILABLE, "subscription_unavailable",
                    "Не удалось загрузить данные подписки. Попробуйте ещё раз.");
        }
    }

    public record DashboardResponse(UUID accountId, String email, long balance, boolean telegramLinked,
                                    List<SubscriptionItem> subscriptions) { }

    public record SubscriptionItem(UUID id) { }

    public record SubscriptionDetailsResponse(UUID id, Instant expiresAt, boolean active, double usedTrafficGb,
                                              double trafficLimitGb, String connectionUrl) { }
}
