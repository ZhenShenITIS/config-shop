package tg.configshop.external_api.remnawave;

import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;

import java.time.Instant;
import java.util.List;

public interface RemnawaveClient {
    RemnawaveUserResponse createBasicUser (String username, Long telegramId);
    RemnawaveUserResponse getUser (String uuid);
    RemnawaveUserResponse updateSubscription (String uuid, Instant expireAt, Long trafficLimitBytes, Integer hwidDeviceLimit);
    List<Device> getUserDevices (String uuid);


}
