package tg.configshop.services;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tg.configshop.dto.RemnawaveLimitedWebhookEvent;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.RemnawaveUserRef;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.external_api.remnawave.dto.user.UserTraffic;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.impl.ExternalTrafficServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawaveTrafficTest {
    private final BotUserRepository repository = mock(BotUserRepository.class);
    private final RemnawaveClient client = mock(RemnawaveClient.class);
    private final BotUser user = BotUser.builder().id(736L).remnawaveUuid("uuid").remnawaveId(123L).build();

    @Test
    void handlesV3WebhookById() {
        when(repository.findByRemnawaveIdWithLock(123L)).thenReturn(Optional.of(user));
        service(3).handleLimitedWebhook(event(new RemnawaveUserRef(null, 123L)));
        verify(client).updateTrafficLimitAndInternalSquads(user.remnawaveRef(), 2000L, List.of("limited"));
        verify(repository, never()).findByRemnawaveUuidWithLock(any());
    }

    @Test
    void handlesLegacyWebhookAfterSwitchToV3UsingStoredId() {
        when(repository.findByRemnawaveUuidWithLock("uuid")).thenReturn(Optional.of(user));
        service(3).handleLimitedWebhook(event(new RemnawaveUserRef("uuid", null)));
        verify(client).updateTrafficLimitAndInternalSquads(user.remnawaveRef(), 2000L, List.of("limited"));
    }

    @Test
    void fallsBackToUuidBeforeBackfillInV2() {
        user.setRemnawaveId(null);
        when(repository.findByRemnawaveIdWithLock(123L)).thenReturn(Optional.empty());
        when(repository.findByRemnawaveUuidWithLock("uuid")).thenReturn(Optional.of(user));
        service(2).handleLimitedWebhook(event(new RemnawaveUserRef("uuid", 123L)));
        verify(client).updateTrafficLimitAndInternalSquads(user.remnawaveRef(), 2000L, List.of("limited"));
    }

    @Test
    void rejectsConflictingUuidEvenWhenIdIsFound() {
        when(repository.findByRemnawaveIdWithLock(123L)).thenReturn(Optional.of(user));
        assertThrows(IllegalStateException.class,
                () -> service(3).handleLimitedWebhook(event(new RemnawaveUserRef("another-uuid", 123L))));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsConflictingIdWhenFallingBackToUuid() {
        when(repository.findByRemnawaveIdWithLock(999L)).thenReturn(Optional.empty());
        when(repository.findByRemnawaveUuidWithLock("uuid")).thenReturn(Optional.of(user));
        assertThrows(IllegalStateException.class,
                () -> service(2).handleLimitedWebhook(event(new RemnawaveUserRef("uuid", 999L))));
        verifyNoInteractions(client);
    }

    @Test
    void trafficPurchaseUsesIdLookupAndPreservesCalculationAndCallOrder() {
        when(repository.findByRemnawaveIdWithLock(123L)).thenReturn(Optional.of(user));
        when(client.getUser(user.remnawaveRef())).thenReturn(RemnawaveUserResponse.builder()
                .trafficLimitBytes(5000L).userTraffic(new UserTraffic(1000L, 1000L))
                .activeInternalSquads(List.of(new InternalSquad("limited", "limited"))).build());
        service(3).applyTrafficPurchase(user.remnawaveRef(), 10);
        var order = inOrder(client);
        order.verify(client).getUser(user.remnawaveRef());
        order.verify(client).resetUserTraffic(user.remnawaveRef());
        order.verify(client).updateTrafficLimitAndInternalSquads(user.remnawaveRef(),
                4000L + 10L * 1024 * 1024 * 1024, List.of("whitelist"));
    }

    private ExternalTrafficServiceImpl service(int version) {
        var service = new ExternalTrafficServiceImpl(repository, client, new RemnawaveApiVersion(version));
        ReflectionTestUtils.setField(service, "whitelistSquadUuid", "whitelist");
        ReflectionTestUtils.setField(service, "trafficLimitSquadUuid", "limited");
        ReflectionTestUtils.setField(service, "limitGraceBytes", 1000L);
        return service;
    }

    private RemnawaveLimitedWebhookEvent event(RemnawaveUserRef ref) {
        return new RemnawaveLimitedWebhookEvent("user.limited", ref, 1000L, 1000L, List.of("whitelist"));
    }
}
