package tg.configshop.dto;

import tg.configshop.constants.SubscriptionChangeStatus;

import java.util.UUID;

public record SubscriptionChangeResult(UUID id, long price, SubscriptionChangeStatus status) {}
