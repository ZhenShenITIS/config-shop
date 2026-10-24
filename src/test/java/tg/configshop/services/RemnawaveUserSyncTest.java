package tg.configshop.services;
import org.junit.jupiter.api.Test;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.*;
import java.util.UUID;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RemnawaveUserSyncTest {
    @Test void v3ResponseDoesNotEraseStoredUuid() { check(123L, false); }
    @Test void responseWithDifferentIdCannotModifyLocalUser() { check(999L, true); }
    private void check(long remoteId, boolean mismatch) {
        var account = Account.builder().id(UUID.randomUUID()).build();
        var sub = RemnawaveSubscription.builder().id(UUID.randomUUID()).account(account).remnawaveId(123L).remnawaveUuid("old-uuid").build();
        var access = mock(SubscriptionAccessService.class); var client = mock(RemnawaveClient.class);
        when(access.requireOwned(account.getId(), sub.getId())).thenReturn(sub);
        when(client.getUser(sub.remnawaveRef())).thenReturn(RemnawaveUserResponse.builder().id(remoteId).hwidDeviceLimit(3).expireAt(Instant.now().plusSeconds(86400)).build());
        var service = new ExternalSubscriptionService(client, access);
        if (mismatch) assertThrows(IllegalStateException.class, () -> service.getExternalUser(account.getId(), sub.getId()));
        else service.getExternalUser(account.getId(), sub.getId());
        assertEquals("old-uuid", sub.getRemnawaveUuid()); assertEquals(123L, sub.getRemnawaveId());
    }
}
