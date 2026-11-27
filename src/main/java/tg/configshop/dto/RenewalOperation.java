package tg.configshop.dto;

import tg.configshop.constants.SubscriptionChangeStatus;

import java.time.Instant;
import java.util.UUID;

public record RenewalOperation(UUID id, long price, SubscriptionChangeStatus status, boolean charged,
                               Instant expiresAt) {}
