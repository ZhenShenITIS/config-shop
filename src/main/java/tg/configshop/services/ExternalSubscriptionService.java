package tg.configshop.services;

import tg.configshop.model.RemnawaveSubscription;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.dto.UserTrafficInGigabytes;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.util.DateUtil;
import tg.configshop.util.RsaEncryptor;


@Service
@RequiredArgsConstructor
public class ExternalSubscriptionService {
    private final RemnawaveClient remnawaveClient;
    private final UserService userService;

    public RemnawaveUser getExternalUserWithCryptLinkAndSync (long userId, boolean json) {
        BotUser botUser = userService.getUser(userId);
        RemnawaveUserResponse response = remnawaveClient.getUser(botUser.remnawaveRef());
        userService.syncRemnawaveUserWithLocalUser(response, botUser);
        String subLink = null;
        try {
            String subscriptionUrl = response.subscriptionUrl();
            if (json) {
                subscriptionUrl += "/json";
            }
            subLink = RsaEncryptor.encryptAndBuildLink(subscriptionUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return RemnawaveUser
                .builder()
                .expireAt(response.expireAt())
                .subscriptionUrl(subLink)
                .daysLeft(DateUtil.getDaysLeft(response.expireAt()))
                .hwidDeviceLimit(response.hwidDeviceLimit())
                .userTraffic(new UserTrafficInGigabytes(response.userTraffic()))
                .uuid(response.uuid())
                .id(response.id())
                .prettyDateExpireAt(DateUtil.getDateEndSubscription(response.expireAt()))
                .isActive(!DateUtil.isExpired(response.expireAt()))
                .shortUuid(response.shortUuid())
                .telegramId(response.telegramId())
                .username(response.username())
                .trafficLimitGigaBytes(response.trafficLimitBytes() / (1024.0 * 1024 * 1024))
                .build();
    }

    public RemnawaveUser getExternalUser (long userId) {
        BotUser botUser = userService.getUser(userId);
        return getExternalUser(botUser.getSubscription());
    }

    public RemnawaveUser getExternalUser(RemnawaveSubscription subscription) {
        RemnawaveUserResponse response = remnawaveClient.getUser(subscription.remnawaveRef());
        return RemnawaveUser
                .builder()
                .expireAt(response.expireAt())
                .daysLeft(DateUtil.getDaysLeft(response.expireAt()))
                .hwidDeviceLimit(response.hwidDeviceLimit())
                .userTraffic(new UserTrafficInGigabytes(response.userTraffic()))
                .uuid(response.uuid())
                .id(response.id())
                .prettyDateExpireAt(DateUtil.getDateEndSubscription(response.expireAt()))
                .isActive(!DateUtil.isExpired(response.expireAt()))
                .shortUuid(response.shortUuid())
                .telegramId(response.telegramId())
                .username(response.username())
                .trafficLimitGigaBytes(response.trafficLimitBytes() / (1024.0 * 1024 * 1024))
                .build();
    }
}
