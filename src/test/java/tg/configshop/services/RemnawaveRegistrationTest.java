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
import tg.configshop.repositories.*;
import tg.configshop.model.*;
import tg.configshop.services.impl.RegistrationServiceImpl;
import tg.configshop.telegram.TelegramRegistrationService;
import java.util.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawaveRegistrationTest {
    private final BotUserRepository repository = mock(BotUserRepository.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final RemnawaveSubscriptionRepository subscriptions = mock(RemnawaveSubscriptionRepository.class);
    private final ReferralService referrals = mock(ReferralService.class);
    private final RemnawaveClient client = mock(RemnawaveClient.class);
    private final SchedulerService scheduler = mock(SchedulerService.class);
    private final Account account = Account.builder().id(UUID.randomUUID()).build();
    private final UUID subscriptionId = UUID.randomUUID();

    @ParameterizedTest @ValueSource(ints = {2,3})
    void savesIdForNewUser(int version) {
        when(client.createBasicUser("736", 736L)).thenReturn(response(version,123L));
        var user = service(version).registerUser(telegramUser(), null);
        assertEquals(736L, user.getId()); assertEquals(123L, user.getRemnawaveId());
        assertEquals(version == 2 ? "uuid" : null, user.getRemnawaveUuid());
        verify(repository).save(user);
        verify(scheduler).scheduleTrialTrafficChecks(account.getId(), subscriptionId);
    }
    @ParameterizedTest @ValueSource(ints = {2,3})
    void savesIdWhenLinkingExistingPanelUser(int version) {
        when(client.createBasicUser("736",736L)).thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT,"duplicate",
                "{\"errorCode\":\"A019\"}".getBytes(), java.nio.charset.StandardCharsets.UTF_8));
        when(client.getUserByUsername("736")).thenReturn(response(version,123L));
        var user = service(version).registerUser(telegramUser(),null);
        assertEquals(123L,user.getRemnawaveId()); verify(client).getUserByUsername("736");
        verify(scheduler,never()).scheduleTrialTrafficChecks(any(),any());
        verify(scheduler).scheduleSubscriptionNotifications(account.getId(),subscriptionId,response(version,123L).expireAt());
    }
    @ParameterizedTest @ValueSource(ints = {2,3})
    void doesNotSaveRegistrationWithoutId(int version) {
        when(client.createBasicUser("736",736L)).thenReturn(response(version,null));
        var service=service(version);
        assertThrows(IllegalStateException.class, () -> service.registerUser(telegramUser(),null));
        verify(repository,never()).save(any()); verifyNoInteractions(referrals,scheduler,accounts,subscriptions);
    }
    private TelegramRegistrationService service(int version) {
        when(accounts.save(any())).thenReturn(account);
        when(accounts.findById(account.getId())).thenReturn(Optional.of(account));
        when(accounts.findByIdWithLock(account.getId())).thenReturn(Optional.of(account));
        when(subscriptions.saveAndFlush(any())).thenAnswer(i -> { var s=(RemnawaveSubscription)i.getArgument(0); s.setId(subscriptionId); return s; });
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        var common=new RegistrationServiceImpl(accounts,subscriptions,client,new RemnawaveApiVersion(version),scheduler);
        return new TelegramRegistrationService(repository,new AccountService(accounts,mock(BalanceService.class)),common,referrals,client);
    }
    private User telegramUser() { return new User(736L,"Name",false); }
    private RemnawaveUserResponse response(int version,Long id) {
        return RemnawaveUserResponse.builder().id(id).uuid(version==2?"uuid":null).shortUuid("short")
                .expireAt(Instant.parse("2026-10-01T00:00:00Z")).build();
    }
}
