package tg.configshop.events;
import java.util.UUID;
public record ReferralRewardEvent(UUID referrerAccountId, UUID sourceAccountId, long rewardAmount,
                                  int level, long purchaseAmount, double percentage) {}
