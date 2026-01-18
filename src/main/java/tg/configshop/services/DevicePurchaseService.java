package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.PurchaseType;
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.model.BotUser;
import tg.configshop.model.Purchase;
import tg.configshop.repositories.PurchaseRepository;

@Service
@RequiredArgsConstructor
public class DevicePurchaseService {
    private final DeviceService deviceService;
    private final UserService userService;
    private final PurchaseRepository purchaseRepository;


    @Transactional(rollbackFor = Exception.class)
    public void purchaseDevices(Long userId, int deviceCount, long totalPrice)
            throws InsufficientBalanceException, TooManyDevicesException {
        BotUser user = userService.getUser(userId);
        if (user.getBalance() < totalPrice) {
            throw new InsufficientBalanceException();
        }
        userService.decreaseBalance(userId, totalPrice);
        deviceService.addDeviceById(userId, deviceCount);
        purchaseRepository.save(Purchase
                .builder()
                        .purchaseType(PurchaseType.DEVICE)
                        .botUser(user)
                        .paidAmount(totalPrice)
                        .deviceCount(deviceCount)
                .build());

    }
}
