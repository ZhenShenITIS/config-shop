package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.dto.RemnawaveLimitedWebhookEvent;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.RemnawaveUserRef;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.external_api.remnawave.dto.user.UserTraffic;
import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.repositories.RemnawaveSubscriptionRepository;
import tg.configshop.services.ExternalTrafficService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTrafficServiceImpl implements ExternalTrafficService {
    private static final long BYTES_IN_GIGABYTE = 1024L * 1024 * 1024;

    private final RemnawaveSubscriptionRepository subscriptionRepository;
    private final RemnawaveClient remnawaveClient;
    private final RemnawaveApiVersion apiVersion;

    @Value("${remnawave.squads.whitelist-uuid}")
    private String whitelistSquadUuid;

    @Value("${remnawave.squads.traffic-limit-uuid}")
    private String trafficLimitSquadUuid;

    @Value("${remnawave.limit-grace-bytes:1000}")
    private long limitGraceBytes;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleLimitedWebhook(RemnawaveLimitedWebhookEvent event) {
        RemnawaveSubscription subscription = getWebhookUserWithLock(event.user());
        RemnawaveUserRef remoteUser = subscription.remnawaveRef();

        Long trafficLimitBytes = event.trafficLimitBytes();
        if (trafficLimitBytes == null || trafficLimitBytes == 0) {
            log.info("Skip Remnawave limited webhook for user {}: traffic limit is {}", remoteUser, trafficLimitBytes);
            return;
        }

        long usedTrafficBytes = event.usedTrafficBytes() == null ? 0L : event.usedTrafficBytes();
        if (usedTrafficBytes < trafficLimitBytes) {
            log.info("Skip Remnawave limited webhook for user {}: used traffic {} is below limit {}",
                    remoteUser, usedTrafficBytes, trafficLimitBytes);
            return;
        }

        List<String> activeInternalSquads = replaceSquad(
                event.activeInternalSquadUuids() == null ? List.of() : event.activeInternalSquadUuids(),
                whitelistSquadUuid,
                trafficLimitSquadUuid
        );
        long newTrafficLimitBytes = usedTrafficBytes + limitGraceBytes;

        remnawaveClient.updateTrafficLimitAndInternalSquads(remoteUser, newTrafficLimitBytes, activeInternalSquads);
        log.info("Handled Remnawave limited webhook for local user {} / remote user {}: limit {} -> {}, squads {}",
                subscription.getId(), remoteUser, trafficLimitBytes, newTrafficLimitBytes, activeInternalSquads);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyTrafficPurchase(RemnawaveUserRef user, int trafficGb) {
        RemnawaveSubscription subscription = getSubscriptionWithLock(user);
        RemnawaveUserRef remoteUser = subscription.remnawaveRef();
        RemnawaveUserResponse remnawaveUser = remnawaveClient.getUser(remoteUser);

        long currentLimit = remnawaveUser.trafficLimitBytes() == null ? 0L : remnawaveUser.trafficLimitBytes();
        long usedTrafficBytes = getUsedTrafficBytes(remnawaveUser);
        long purchasedBytes = (long) trafficGb * BYTES_IN_GIGABYTE;
        long remainder = currentLimit - usedTrafficBytes;
        long newTrafficLimitBytes = remainder + purchasedBytes;

        if (newTrafficLimitBytes <= 0) {
            log.error("Calculated invalid Remnawave traffic limit for local user {} / remote user {}: currentLimit={}, used={}, trafficGb={}, newLimit={}",
                    subscription.getId(), remoteUser, currentLimit, usedTrafficBytes, trafficGb, newTrafficLimitBytes);
            throw new IllegalStateException("Calculated Remnawave traffic limit must be positive");
        }

        List<String> activeInternalSquads = replaceSquad(
                getActiveInternalSquadUuids(remnawaveUser),
                trafficLimitSquadUuid,
                whitelistSquadUuid
        );

        remnawaveClient.resetUserTraffic(remoteUser);
        remnawaveClient.updateTrafficLimitAndInternalSquads(remoteUser, newTrafficLimitBytes, activeInternalSquads);

        log.info("Applied Remnawave traffic purchase for local user {} / remote user {}: currentLimit={}, used={}, purchasedGb={}, newLimit={}, squads={}",
                subscription.getId(), remoteUser, currentLimit, usedTrafficBytes, trafficGb, newTrafficLimitBytes, activeInternalSquads);
    }

    private RemnawaveSubscription getSubscriptionWithLock(RemnawaveUserRef user) {
        RemnawaveUserRef selected = apiVersion.select(user);
        RemnawaveSubscription subscription = (apiVersion.isV2()
                ? subscriptionRepository.findByRemnawaveUuidWithLock(selected.uuid())
                : subscriptionRepository.findByRemnawaveIdWithLock(selected.id()))
                .orElseThrow(() -> new IllegalStateException("User not found by Remnawave reference: " + user));
        validateUserReference(user, subscription);
        return subscription;
    }

    private RemnawaveSubscription getWebhookUserWithLock(RemnawaveUserRef user) {
        Optional<RemnawaveSubscription> found = user.id() == null
                ? Optional.empty()
                : subscriptionRepository.findByRemnawaveIdWithLock(user.id());
        if (found.isEmpty() && user.uuid() != null && !user.uuid().isBlank()) {
            found = subscriptionRepository.findByRemnawaveUuidWithLock(user.uuid());
        }
        RemnawaveSubscription subscription = found.orElseThrow(
                () -> new IllegalStateException("Webhook user not found: " + user));
        validateUserReference(user, subscription);
        return subscription;
    }

    private void validateUserReference(RemnawaveUserRef user, RemnawaveSubscription subscription) {
        boolean conflictingId = user.id() != null && subscription.getRemnawaveId() != null
                && !Objects.equals(user.id(), subscription.getRemnawaveId());
        boolean conflictingUuid = user.uuid() != null && !user.uuid().isBlank()
                && !Objects.equals(user.uuid(), subscription.getRemnawaveUuid());
        if (conflictingId || conflictingUuid) {
            throw new IllegalStateException("Conflicting Remnawave identifiers for local user " + subscription.getId());
        }
    }

    private long getUsedTrafficBytes(RemnawaveUserResponse remnawaveUser) {
        UserTraffic userTraffic = remnawaveUser.userTraffic();
        return userTraffic == null || userTraffic.usedTrafficBytes() == null ? 0L : userTraffic.usedTrafficBytes();
    }

    private List<String> getActiveInternalSquadUuids(RemnawaveUserResponse remnawaveUser) {
        if (remnawaveUser.activeInternalSquads() == null) {
            return List.of();
        }

        return remnawaveUser.activeInternalSquads().stream()
                .map(InternalSquad::uuid)
                .filter(uuid -> uuid != null && !uuid.isBlank())
                .toList();
    }

    private List<String> replaceSquad(List<String> currentSquads, String squadToRemove, String squadToAdd) {
        Set<String> updatedSquads = new LinkedHashSet<>(currentSquads);
        updatedSquads.remove(squadToRemove);
        updatedSquads.add(squadToAdd);
        return List.copyOf(updatedSquads);
    }
}
