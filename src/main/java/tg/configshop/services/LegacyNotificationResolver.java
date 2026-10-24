package tg.configshop.services;
import java.util.List;
import java.util.UUID;
/** Translates recipient keys saved by earlier application versions. */
public interface LegacyNotificationResolver {
    UUID accountId(Long legacyRecipientId);
    UUID subscriptionId(Long legacyRecipientId);
    List<Long> recipientIds(UUID accountId);
}
