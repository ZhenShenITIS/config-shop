package tg.configshop.external_api.remnawave;

import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;

import java.time.Instant;
import java.util.List;

public interface RemnawaveClient {
    RemnawaveUserResponse createBasicUser (String username, Long telegramId);
    RemnawaveUserResponse getUser (RemnawaveUserRef user);
    RemnawaveUserResponse getUserByUsername (String username);
    RemnawaveUserResponse updateSubscription (RemnawaveUserRef user, Instant expireAt, Integer hwidDeviceLimit);
    RemnawaveUserResponse updateTrafficLimit (RemnawaveUserRef user, Long trafficLimitBytes);
    RemnawaveUserResponse resetUserTraffic (RemnawaveUserRef user);
    RemnawaveUserResponse updateTrafficLimitAndInternalSquads (RemnawaveUserRef user, Long trafficLimitBytes, List<String> activeInternalSquads);
    List<Device> getUserDevices (RemnawaveUserRef user);
    void deleteDevice (RemnawaveUserRef user, String hwid);
    void updateDeviceCount (RemnawaveUserRef user, int countOfDevices);


}
