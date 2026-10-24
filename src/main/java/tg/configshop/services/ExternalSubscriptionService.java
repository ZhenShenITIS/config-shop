package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.dto.UserTrafficInGigabytes;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.util.DateUtil;
import tg.configshop.util.RsaEncryptor;
import java.util.Objects;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ExternalSubscriptionService {
    private final RemnawaveClient remnawaveClient;
    private final SubscriptionAccessService subscriptions;

    public RemnawaveUser getExternalUser(UUID accountId, UUID subscriptionId) {
        return load(accountId, subscriptionId, false, false);
    }
    public RemnawaveUser getExternalUserWithCryptLinkAndSync(UUID accountId, UUID subscriptionId, boolean json) {
        return load(accountId, subscriptionId, true, json);
    }
    private RemnawaveUser load(UUID accountId, UUID subscriptionId, boolean link, boolean json) {
        var subscription = subscriptions.requireOwned(accountId, subscriptionId);
        var response = remnawaveClient.getUser(subscription.remnawaveRef());
        if (subscription.getRemnawaveId() != null && !Objects.equals(subscription.getRemnawaveId(), response.id()))
            throw new IllegalStateException("Conflicting Remnawave ID for subscription " + subscriptionId);
        String url = null;
        if (link) {
            try { url = RsaEncryptor.encryptAndBuildLink(response.subscriptionUrl() + (json ? "/json" : "")); }
            catch (Exception e) { throw new IllegalStateException("Cannot prepare connection link", e); }
        }
        return RemnawaveUser.builder().expireAt(response.expireAt()).subscriptionUrl(url)
                .daysLeft(DateUtil.getDaysLeft(response.expireAt())).hwidDeviceLimit(response.hwidDeviceLimit())
                .userTraffic(response.userTraffic() == null ? new UserTrafficInGigabytes(0, 0) : new UserTrafficInGigabytes(response.userTraffic()))
                .uuid(response.uuid()).id(response.id()).shortUuid(response.shortUuid()).username(response.username())
                .trafficLimitGigaBytes(response.trafficLimitBytes() == null ? 0 : response.trafficLimitBytes() / (1024.0 * 1024 * 1024))
                .isActive(!DateUtil.isExpired(response.expireAt())).build();
    }
}
