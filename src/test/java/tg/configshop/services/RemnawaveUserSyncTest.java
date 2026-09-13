package tg.configshop.services;

import org.junit.jupiter.api.Test;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.impl.UserServiceImpl;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawaveUserSyncTest {
    private final BotUserRepository repository = mock(BotUserRepository.class);
    private final UserServiceImpl service = new UserServiceImpl(repository, mock(TopUpRepository.class));

    @Test
    void v3ResponseDoesNotEraseStoredUuid() {
        BotUser user = BotUser.builder().id(736L).remnawaveUuid("old-uuid").remnawaveId(123L).build();
        Instant expiry = Instant.parse("2026-10-01T00:00:00Z");
        service.syncRemnawaveUserWithLocalUser(RemnawaveUserResponse.builder().id(123L).expireAt(expiry).build(), user);
        assertEquals("old-uuid", user.getRemnawaveUuid());
        assertEquals(123L, user.getRemnawaveId());
        assertEquals(expiry, user.getExpireAt());
        verify(repository).save(user);
    }

    @Test
    void responseWithDifferentIdCannotModifyLocalUser() {
        BotUser user = BotUser.builder().id(736L).remnawaveUuid("old-uuid").remnawaveId(123L).build();
        assertThrows(IllegalStateException.class, () -> service.syncRemnawaveUserWithLocalUser(
                RemnawaveUserResponse.builder().id(999L).build(), user));
        verifyNoInteractions(repository);
        assertEquals(123L, user.getRemnawaveId());
    }
}
