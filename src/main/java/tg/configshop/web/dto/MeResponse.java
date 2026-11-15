package tg.configshop.web.dto;

import java.time.Instant;
import java.util.UUID;

public record MeResponse(UUID webUserId, UUID accountId, String email, Instant emailVerifiedAt) {
}
