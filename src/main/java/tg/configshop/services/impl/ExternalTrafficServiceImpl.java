package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.dto.RemnawaveLimitedWebhookEvent;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.external_api.remnawave.dto.user.UserTraffic;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.ExternalTrafficService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalTrafficServiceImpl implements ExternalTrafficService {
    private static final long BYTES_IN_GIGABYTE = 1024L * 1024 * 1024;

    private final BotUserRepository botUserRepository;
    private final RemnawaveClient remnawaveClient;

    @Value("${remnawave.squads.whitelist-uuid}")
    private String whitelistSquadUuid;

    @Value("${remnawave.squads.traffic-limit-uuid}")
    private String trafficLimitSquadUuid;

    @Value("${remnawave.limit-grace-bytes:1000}")
    private long limitGraceBytes;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleLimitedWebhook(RemnawaveLimitedWebhookEvent event) {
        String remnawaveUuid = event.remnawaveUuid();
        BotUser botUser = getBotUserWithLock(remnawaveUuid);

        Long trafficLimitBytes = event.trafficLimitBytes();
        if (trafficLimitBytes == null || trafficLimitBytes == 0) {
            log.info("Skip Remnawave limited webhook for user {}: traffic limit is {}", remnawaveUuid, trafficLimitBytes);
            return;
        }

        long usedTrafficBytes = event.usedTrafficBytes() == null ? 0L : event.usedTrafficBytes();
        if (usedTrafficBytes < trafficLimitBytes) {
            log.info("Skip Remnawave limited webhook for user {}: used traffic {} is below limit {}",
                    remnawaveUuid, usedTrafficBytes, trafficLimitBytes);
            return;
        }

        List<String> activeInternalSquads = replaceSquad(
                event.activeInternalSquadUuids() == null ? List.of() : event.activeInternalSquadUuids(),
                whitelistSquadUuid,
                trafficLimitSquadUuid
        );
        long newTrafficLimitBytes = usedTrafficBytes + limitGraceBytes;

        remnawaveClient.updateTrafficLimitAndInternalSquads(remnawaveUuid, newTrafficLimitBytes, activeInternalSquads);
        log.info("Handled Remnawave limited webhook for local user {} / remote user {}: limit {} -> {}, squads {}",
                botUser.getId(), remnawaveUuid, trafficLimitBytes, newTrafficLimitBytes, activeInternalSquads);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyTrafficPurchase(String remnawaveUuid, int trafficGb) {
        BotUser botUser = getBotUserWithLock(remnawaveUuid);
        RemnawaveUserResponse remnawaveUser = remnawaveClient.getUser(remnawaveUuid);

        long currentLimit = remnawaveUser.trafficLimitBytes() == null ? 0L : remnawaveUser.trafficLimitBytes();
        long usedTrafficBytes = getUsedTrafficBytes(remnawaveUser);
        long purchasedBytes = (long) trafficGb * BYTES_IN_GIGABYTE;
        long remainder = currentLimit - usedTrafficBytes;
        long newTrafficLimitBytes = remainder + purchasedBytes;

        if (newTrafficLimitBytes <= 0) {
            log.error("Calculated invalid Remnawave traffic limit for local user {} / remote user {}: currentLimit={}, used={}, trafficGb={}, newLimit={}",
                    botUser.getId(), remnawaveUuid, currentLimit, usedTrafficBytes, trafficGb, newTrafficLimitBytes);
            throw new IllegalStateException("Calculated Remnawave traffic limit must be positive");
        }

        List<String> activeInternalSquads = replaceSquad(
                getActiveInternalSquadUuids(remnawaveUser),
                trafficLimitSquadUuid,
                whitelistSquadUuid
        );

        remnawaveClient.resetUserTraffic(remnawaveUuid);
        remnawaveClient.updateTrafficLimitAndInternalSquads(remnawaveUuid, newTrafficLimitBytes, activeInternalSquads);

        log.info("Applied Remnawave traffic purchase for local user {} / remote user {}: currentLimit={}, used={}, purchasedGb={}, newLimit={}, squads={}",
                botUser.getId(), remnawaveUuid, currentLimit, usedTrafficBytes, trafficGb, newTrafficLimitBytes, activeInternalSquads);
    }

    private BotUser getBotUserWithLock(String remnawaveUuid) {
        return botUserRepository.findByRemnawaveUuidWithLock(remnawaveUuid)
                .orElseThrow(() -> new RuntimeException("User not found by Remnawave UUID: " + remnawaveUuid));
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
