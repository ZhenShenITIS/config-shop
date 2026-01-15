package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DeviceService {
    private final RemnawaveClient remnawaveClient;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    public List<Device> getDevicesByUserId(Long userId) {
        String uuid = userService.getUser(userId).getRemnawaveUuid();
        return remnawaveClient.getUserDevices(uuid);
    }

    public void deleteDeviceById(Long userId, String hwid) {
        String uuid = userService.getUser(userId).getRemnawaveUuid();
        remnawaveClient.deleteDevice(uuid, hwid);
    }

    public void addDeviceById(Long userId, int deviceToAdd) throws TooManyDevicesException {
        String uuid = userService.getUser(userId).getRemnawaveUuid();
        RemnawaveUserResponse response = remnawaveClient.getUser(uuid);
        int currentDeviceCount = response.hwidDeviceLimit();
        int newDeviceCount = currentDeviceCount + deviceToAdd;
        if (newDeviceCount > subscriptionService.getMaxDeviceCount()) {
            throw new TooManyDevicesException("Cannot add more devices than the maximum allowed.");
        }
        remnawaveClient.updateDeviceCount(uuid, newDeviceCount);
    }

}
