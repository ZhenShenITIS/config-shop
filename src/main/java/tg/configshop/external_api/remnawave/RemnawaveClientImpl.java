package tg.configshop.external_api.remnawave;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.device.DeviceRootResponse;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquadsRootResponse;
import tg.configshop.external_api.remnawave.dto.user.RemnaveUserUpdateRequest;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserRequest;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserRootResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@AllArgsConstructor
public class RemnawaveClientImpl implements RemnawaveClient {
    private final RestClient remnawaveRestClient;

    private final long TRIAL_PERIOD_IN_SECONDS = 432000;
    private final long TRIAL_TRAFFIC_IN_BYTES = 50L * 1024 * 1024 * 1024;
    private final int TRIAL_HWID_DEVICE_LIMIT = 2;

    @Override
    public RemnawaveUserResponse createBasicUser(String username, Long telegramId) {
        return remnawaveRestClient.post()
                .uri("/api/users")
                .body(new RemnawaveUserRequest(username, Instant.now().plusSeconds(TRIAL_PERIOD_IN_SECONDS), telegramId, TRIAL_TRAFFIC_IN_BYTES, TRIAL_HWID_DEVICE_LIMIT, getInternalSquads()))
                .retrieve()
                .body(RemnawaveUserRootResponse.class).response();
    }

    @Override
    public RemnawaveUserResponse getUser(String uuid) {
        return remnawaveRestClient.get()
                .uri("/api/users/{uuid}", uuid)
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();
    }

    @Override
    public RemnawaveUserResponse updateSubscription(String uuid, Instant expireAt, Long trafficLimitBytes, Integer hwidDeviceLimit) {
        return remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new RemnaveUserUpdateRequest(uuid, expireAt, trafficLimitBytes, hwidDeviceLimit))
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();

    }

    @Override
    public List<Device> getUserDevices(String uuid) {
        return remnawaveRestClient.get()
                .uri("/api/hwid/devices/{uuid}", uuid)
                .retrieve()
                .body(DeviceRootResponse.class)
                .response()
                .devices();
    }

    private List<String> getInternalSquads() {
        List<InternalSquad> internalSquadList = remnawaveRestClient.get()
                .uri("/api/internal-squads")
                .retrieve()
                .body(InternalSquadsRootResponse.class)
                .response()
                .internalSquads();
        List<String> result = new ArrayList<>();
        for (InternalSquad is : internalSquadList) {
            if (!is.name().contains("serv")) {
                result.add(is.uuid());
            }
        }
        return result;
    }
}
