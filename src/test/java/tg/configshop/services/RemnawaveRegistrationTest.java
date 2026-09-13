package tg.configshop.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.telegram.telegrambots.meta.api.objects.User;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.impl.RegistrationServiceImpl;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawaveRegistrationTest {
    private final BotUserRepository repository = mock(BotUserRepository.class);
    private final ReferralService referrals = mock(ReferralService.class);
    private final RemnawaveClient client = mock(RemnawaveClient.class);
    private final SchedulerService scheduler = mock(SchedulerService.class);

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void savesIdForNewUser(int version) {
        when(client.createBasicUser("736", 736L)).thenReturn(response(version, 123L));
        var user = service(version).registerUser(telegramUser(), null);
        assertEquals(736L, user.getId());
        assertEquals(123L, user.getRemnawaveId());
        assertEquals(version == 2 ? "uuid" : null, user.getRemnawaveUuid());
        verify(repository).save(user);
        verify(scheduler).scheduleTrialTrafficChecks(736L);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void savesIdWhenLinkingExistingPanelUser(int version) {
        when(client.createBasicUser("736", 736L)).thenThrow(
                new HttpClientErrorException(HttpStatus.CONFLICT, "duplicate",
                        "{\"errorCode\":\"A019\"}".getBytes(), java.nio.charset.StandardCharsets.UTF_8));
        when(client.getUserByUsername("736")).thenReturn(response(version, 123L));
        var user = service(version).registerUser(telegramUser(), null);
        assertEquals(123L, user.getRemnawaveId());
        verify(client).getUserByUsername("736");
        verify(scheduler, never()).scheduleTrialTrafficChecks(anyLong());
        verify(scheduler).scheduleSubscriptionNotifications(736L, user.getExpireAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void doesNotSaveRegistrationWithoutId(int version) {
        when(client.createBasicUser("736", 736L)).thenReturn(response(version, null));
        assertThrows(IllegalStateException.class, () -> service(version).registerUser(telegramUser(), null));
        verifyNoInteractions(repository, referrals, scheduler);
    }

    private RegistrationServiceImpl service(int version) {
        return new RegistrationServiceImpl(repository, referrals, client, scheduler, new RemnawaveApiVersion(version));
    }

    private User telegramUser() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(736L);
        when(user.getUserName()).thenReturn("telegram_handle");
        return user;
    }

    private RemnawaveUserResponse response(int version, Long id) {
        return RemnawaveUserResponse.builder().id(id).uuid(version == 2 ? "uuid" : null)
                .shortUuid("short").expireAt(Instant.parse("2026-10-01T00:00:00Z")).build();
    }
}
