package tg.configshop.services.impl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import tg.configshop.dto.InitialSubscriptionData;
import tg.configshop.dto.SubscriptionSummary;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.repositories.RemnawaveSubscriptionRepository;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.services.RegistrationService;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {
    private final AccountRepository accounts;
    private final RemnawaveSubscriptionRepository subscriptions;
    private final RemnawaveClient client;
    private final RemnawaveApiVersion apiVersion;
    private final SchedulerService scheduler;

    @Override @Transactional
    public SubscriptionSummary createTrialSubscription(UUID accountId) {
        accounts.findByIdWithLock(accountId).orElseThrow();
        var existing = subscriptions.findByAccount_IdOrderByCreatedAtAscIdAsc(accountId);
        if (existing.size() > 1) throw new IllegalStateException("Initial subscription already exists");
        if (existing.size() == 1) return summary(existing.getFirst());
        String externalKey = "a_" + accountId.toString().replace("-", "");
        boolean created = true;
        tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse remote;
        try { remote = client.createBasicUser(externalKey); }
        catch (RestClientResponseException e) {
            if (!usernameAlreadyExists(e)) throw e;
            remote = client.getUserByUsername(externalKey);
            created = false;
        }
        return registerInitialSubscription(accountId, new InitialSubscriptionData(remote.id(), remote.uuid(),
                remote.shortUuid(), remote.expireAt(), created));
    }
    public static boolean usernameAlreadyExists(RestClientResponseException e) {
        return e.getStatusCode().is4xxClientError() && (e.getResponseBodyAsString().contains("\"errorCode\":\"A019\"")
                || e.getResponseBodyAsString().contains("User username already exists"));
    }
    @Override @Transactional
    public SubscriptionSummary registerInitialSubscription(UUID accountId, InitialSubscriptionData initial) {
        var account = accounts.findByIdWithLock(accountId).orElseThrow();
        var existing = subscriptions.findByAccount_IdOrderByCreatedAtAscIdAsc(accountId);
        if (!existing.isEmpty()) {
            return existing.stream().filter(s -> java.util.Objects.equals(s.getRemnawaveId(), initial.externalId()))
                    .findFirst().map(this::summary).orElseThrow(() -> new IllegalStateException("Initial subscription already exists"));
        }
        if (initial.externalId() == null || initial.externalId() <= 0) throw new IllegalStateException("Remnawave did not return a positive user ID");
        var subscription = RemnawaveSubscription.builder().account(account).remnawaveId(initial.externalId())
                .remnawaveUuid(initial.externalUuid()).shortId(initial.shortId()).build();
        apiVersion.select(subscription.remnawaveRef());
        subscriptions.saveAndFlush(subscription);
        account.getSubscriptions().add(subscription);
        if (initial.created()) scheduler.scheduleTrialTrafficChecks(accountId, subscription.getId());
        else scheduler.scheduleSubscriptionNotifications(accountId, subscription.getId(), initial.expiresAt());
        return summary(subscription);
    }
    private SubscriptionSummary summary(RemnawaveSubscription value) {
        return new SubscriptionSummary(value.getId(), value.getAccount().getId(), value.getShortId());
    }
}
