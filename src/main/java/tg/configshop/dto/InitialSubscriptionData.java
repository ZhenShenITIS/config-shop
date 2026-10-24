package tg.configshop.dto;
import java.time.Instant;
public record InitialSubscriptionData(Long externalId, String externalUuid, String shortId,
                                      Instant expiresAt, boolean created) {}
