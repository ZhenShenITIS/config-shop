package tg.configshop.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReferralRewardEvent {
    private final long referrerId;
    private final long sourceUserId;
    private final long rewardAmount;
    private final int level;
    private final long purchaseAmount;
    private final double percentage;
}
