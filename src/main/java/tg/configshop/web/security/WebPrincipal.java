package tg.configshop.web.security;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

public record WebPrincipal(UUID webUserId, UUID accountId, String email, Instant emailVerifiedAt)
        implements Principal {
    @Override
    public String getName() {
        return webUserId.toString();
    }
}
