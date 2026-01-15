package tg.configshop.services;

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

    public RemnawaveUser getExternalUserWithCryptLinkAndSync (long userId) {
        BotUser botUser = userService.getUser(userId);
        String remnawaveUuid = botUser.getRemnawaveUuid();
        RemnawaveUserResponse response = remnawaveClient.getUser(remnawaveUuid);
        userService.syncRemnawaveUserWithLocalUser(response, botUser);
        String subLink = null;
        try {
            subLink = RsaEncryptor.encryptAndBuildLink(response.subscriptionUrl());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return RemnawaveUser
                .builder()
                .expireAt(response.expireAt())
                .subscriptionUrl(subLink)
                .daysLeft(DateUtil.getDaysLeft(botUser))
                .hwidDeviceLimit(response.hwidDeviceLimit())
                .userTraffic(new UserTrafficInGigabytes(response.userTraffic()))
                .uuid(response.uuid())
                .prettyDateExpireAt(DateUtil.getDateEndSubscription(botUser))
                .isActive(!DateUtil.isExpired(botUser))
                .shortUuid(response.shortUuid())
                .telegramId(response.telegramId())
                .username(response.username())
                .trafficLimitGigaBytes(response.trafficLimitBytes() / (1024.0 * 1024 * 1024))
                .build();
    }

    public RemnawaveUser getExternalUser (long userId) {
        BotUser botUser = userService.getUser(userId);
        String remnawaveUuid = botUser.getRemnawaveUuid();
        RemnawaveUserResponse response = remnawaveClient.getUser(remnawaveUuid);
        return RemnawaveUser
                .builder()
                .expireAt(response.expireAt())
                .daysLeft(DateUtil.getDaysLeft(botUser))
                .hwidDeviceLimit(response.hwidDeviceLimit())
                .userTraffic(new UserTrafficInGigabytes(response.userTraffic()))
                .uuid(response.uuid())
                .prettyDateExpireAt(DateUtil.getDateEndSubscription(botUser))
                .isActive(!DateUtil.isExpired(botUser))
                .shortUuid(response.shortUuid())
                .telegramId(response.telegramId())
                .username(response.username())
                .trafficLimitGigaBytes(response.trafficLimitBytes() / (1024.0 * 1024 * 1024))
                .build();
    }
}
