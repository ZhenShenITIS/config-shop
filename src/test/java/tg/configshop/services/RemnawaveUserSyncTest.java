package tg.configshop.services;

import org.junit.jupiter.api.Test;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.impl.UserServiceImpl;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawaveUserSyncTest {
    private final BotUserRepository repository = mock(BotUserRepository.class);
    private final UserServiceImpl service = new UserServiceImpl(repository, mock(BalanceService.class));

    @Test
    void v3ResponseDoesNotEraseStoredUuid() {
        BotUser user = tg.configshop.TestUsers.user(736L, "old-uuid", 123L);
        Instant expiry = Instant.parse("2026-10-01T00:00:00Z");
        service.syncRemnawaveUserWithLocalUser(RemnawaveUserResponse.builder().id(123L).expireAt(expiry).build(), user);
        assertEquals("old-uuid", user.getRemnawaveUuid());
        assertEquals(123L, user.getRemnawaveId());
        verifyNoInteractions(repository);
    }

    @Test
    void responseWithDifferentIdCannotModifyLocalUser() {
        BotUser user = tg.configshop.TestUsers.user(736L, "old-uuid", 123L);
        assertThrows(IllegalStateException.class, () -> service.syncRemnawaveUserWithLocalUser(
                RemnawaveUserResponse.builder().id(999L).build(), user));
        verifyNoInteractions(repository);
        assertEquals(123L, user.getRemnawaveId());
    }
}
