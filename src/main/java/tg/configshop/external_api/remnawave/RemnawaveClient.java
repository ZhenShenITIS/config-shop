package tg.configshop.external_api.remnawave;

import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;

import java.time.Instant;
import java.util.List;

public interface RemnawaveClient {
    RemnawaveUserResponse createBasicUser (String username, Long telegramId);
    RemnawaveUserResponse getUser (String uuid);
    RemnawaveUserResponse getUserByUsername (String username);
    RemnawaveUserResponse updateSubscription (String uuid, Instant expireAt, Integer hwidDeviceLimit);
    RemnawaveUserResponse updateTrafficLimit (String uuid, Long trafficLimitBytes);
    RemnawaveUserResponse resetUserTraffic (String uuid);
    RemnawaveUserResponse updateTrafficLimitAndInternalSquads (String uuid, Long trafficLimitBytes, List<String> activeInternalSquads);
    List<Device> getUserDevices (String uuid);
    void deleteDevice (String uuid, String hwid);
    void updateDeviceCount (String uuid, int countOfDevices);


}
