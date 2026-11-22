package tg.configshop.dto;

import java.util.List;

public record AccountOverview(long balance, boolean linkedProfiles, List<SubscriptionSummary> subscriptions) { }
