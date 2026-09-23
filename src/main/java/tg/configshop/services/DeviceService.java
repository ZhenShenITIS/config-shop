package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.RemnawaveUserRef;
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
        RemnawaveUserRef user = userService.getUser(userId).remnawaveRef();
        return remnawaveClient.getUserDevices(user);
    }

    public void deleteDeviceById(Long userId, String hwid) {
        RemnawaveUserRef user = userService.getUser(userId).remnawaveRef();
        remnawaveClient.deleteDevice(user, hwid);
    }

    public void addDeviceById(Long userId, int deviceToAdd) throws TooManyDevicesException {
        RemnawaveUserRef user = userService.getUser(userId).remnawaveRef();
        RemnawaveUserResponse response = remnawaveClient.getUser(user);
        int currentDeviceCount = response.hwidDeviceLimit();
        int newDeviceCount = currentDeviceCount + deviceToAdd;
        if (newDeviceCount > subscriptionService.getMaxDeviceCount()) {
            throw new TooManyDevicesException("Cannot add more devices than the maximum allowed.");
        }
        remnawaveClient.updateDeviceCount(user, newDeviceCount);
    }

}
