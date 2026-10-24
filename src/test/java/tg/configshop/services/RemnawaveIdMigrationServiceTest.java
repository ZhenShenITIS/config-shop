package tg.configshop.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.repositories.RemnawaveSubscriptionRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawaveIdMigrationServiceTest {
    private final RemnawaveSubscriptionRepository repository = mock(RemnawaveSubscriptionRepository.class);
    private final RemnawaveClient client = mock(RemnawaveClient.class);
    private final RemnawaveIdMigrationService service =
            new RemnawaveIdMigrationService(repository, client, new RemnawaveApiVersion(2));

    @Test
    void fillsIdsAndRepeatedRunDoesNotTouchCompletedUsers() {
        RemnawaveSubscription user = user(736L);
        when(repository.findAllByRemnawaveIdIsNullOrderByIdAsc()).thenReturn(List.of(user), List.of());
        when(client.getUser(user.remnawaveRef())).thenReturn(response(user, 123L));
        when(repository.updateRemnawaveId(new java.util.UUID(0,736L), 123L)).thenReturn(1);

        var first = service.migrate();
        assertEquals(1, first.processed());
        assertEquals(1, first.updated());
        assertEquals(0, first.remaining());
        assertTrue(first.errors().isEmpty());
        assertEquals(0, service.migrate().processed());
        verify(client, times(1)).getUser(user.remnawaveRef());
        verify(repository, times(1)).updateRemnawaveId(new java.util.UUID(0,736L), 123L);
        verify(repository, never()).save(any());
    }

    @Test
    void continuesAfterAnErrorAndDoesNotExposeResponseBody() {
        RemnawaveSubscription missing = user(1L);
        RemnawaveSubscription valid = user(2L);
        when(repository.findAllByRemnawaveIdIsNullOrderByIdAsc()).thenReturn(List.of(missing, valid));
        when(client.getUser(missing.remnawaveRef())).thenThrow(
                new HttpClientErrorException(HttpStatus.NOT_FOUND, "SECRET RESPONSE"));
        when(client.getUser(valid.remnawaveRef())).thenReturn(response(valid, 22L));
        when(repository.updateRemnawaveId(new java.util.UUID(0,2L), 22L)).thenReturn(1);
        when(repository.countByRemnawaveIdIsNull()).thenReturn(1L);

        var result = service.migrate();
        assertEquals(2, result.processed());
        assertEquals(1, result.updated());
        assertEquals(1, result.remaining());
        assertEquals(List.of(new java.util.UUID(0,1) + ": HTTP 404"), result.errors());
        assertFalse(result.report().contains("SECRET"));
        verify(repository, never()).updateRemnawaveId(eq(new java.util.UUID(0,1L)), any());
    }

    @Test
    void rejectsMismatchedUuidAndMissingId() {
        RemnawaveSubscription first = user(1L);
        RemnawaveSubscription second = user(2L);
        when(repository.findAllByRemnawaveIdIsNullOrderByIdAsc()).thenReturn(List.of(first, second));
        when(client.getUser(first.remnawaveRef())).thenReturn(RemnawaveUserResponse.builder().uuid("wrong").id(1L).build());
        when(client.getUser(second.remnawaveRef())).thenReturn(response(second, null));
        var result = service.migrate();
        assertEquals(0, result.updated());
        assertEquals(2, result.errors().size());
        verify(repository, never()).updateRemnawaveId(any(), any());
    }

    @Test
    void stopsOnAuthenticationFailure() {
        RemnawaveSubscription first = user(1L);
        RemnawaveSubscription second = user(2L);
        when(repository.findAllByRemnawaveIdIsNullOrderByIdAsc()).thenReturn(List.of(first, second));
        when(client.getUser(first.remnawaveRef())).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        assertEquals(1, service.migrate().processed());
        verify(client, never()).getUser(second.remnawaveRef());
    }

    @Test
    void rejectsV3WithoutReadingDatabaseOrPanel() {
        var v3 = new RemnawaveIdMigrationService(repository, client, new RemnawaveApiVersion(3));
        assertThrows(IllegalStateException.class, v3::migrate);
        verifyNoInteractions(repository, client);
    }

    private RemnawaveSubscription user(Long id) {
        return RemnawaveSubscription.builder().id(new java.util.UUID(0,id)).remnawaveUuid("uuid-" + id).build();
    }

    private RemnawaveUserResponse response(RemnawaveSubscription user, Long id) {
        return RemnawaveUserResponse.builder().uuid(user.getRemnawaveUuid()).id(id).build();
    }
}
