package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.model.BotUser;

@Service
@RequiredArgsConstructor
public class DevicePurchaseService {
    private final DeviceService deviceService;
    private final UserService userService;

    @Transactional(rollbackFor = Exception.class)
    public void purchaseDevices(Long userId, int deviceCount, long totalPrice)
            throws InsufficientBalanceException, TooManyDevicesException {
        BotUser user = userService.getUser(userId);
        if (user.getBalance() < totalPrice) {
            throw new InsufficientBalanceException();
        }
        userService.decreaseBalance(userId, totalPrice);
        deviceService.addDeviceById(userId, deviceCount);
    }
}
