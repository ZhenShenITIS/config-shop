package tg.configshop.services;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tg.configshop.dto.DeviceView;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class DeviceService {
    private final RemnawaveClient remnawaveClient;
    private final SubscriptionAccessService subscriptions;

    public List<DeviceView> getDevices(UUID accountId, UUID subscriptionId) {
        var ref = subscriptions.requireOwned(accountId, subscriptionId).remnawaveRef();
        return remnawaveClient.getUserDevices(ref).stream().map(d -> new DeviceView(d.hwid(), d.platform(),
                d.osVersion(), d.deviceModel(), d.userAgent(), d.createdAt(), d.updatedAt())).toList();
    }
    public void deleteDeviceById(UUID accountId, UUID subscriptionId, String hwid) {
        remnawaveClient.deleteDevice(subscriptions.requireOwned(accountId, subscriptionId).remnawaveRef(), hwid);
    }
}
