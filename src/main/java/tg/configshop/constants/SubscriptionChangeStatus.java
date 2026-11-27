package tg.configshop.constants;

import java.util.Set;

public enum SubscriptionChangeStatus {
    PREPARING, APPLYING, APPLIED, COMPLETED, INSUFFICIENT_BALANCE, MANUAL_REVIEW;

    public static final Set<SubscriptionChangeStatus> RETRYABLE = Set.of(PREPARING, APPLYING, APPLIED);
    public static final Set<SubscriptionChangeStatus> ACTIVE = Set.of(PREPARING, APPLYING, APPLIED, MANUAL_REVIEW);
}
