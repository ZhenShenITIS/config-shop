package tg.configshop.web.dto;

import java.util.UUID;

public record ConfirmEmailResponse(boolean emailVerified, boolean trialCreated, UUID accountId, String message) {
}
