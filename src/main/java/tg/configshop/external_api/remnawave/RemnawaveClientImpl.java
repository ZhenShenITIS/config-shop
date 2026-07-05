package tg.configshop.external_api.remnawave;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tg.configshop.external_api.remnawave.dto.device.AddDeviceRequest;
import tg.configshop.external_api.remnawave.dto.device.DeleteDeviceRequest;
import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.device.DeviceRootResponse;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquad;
import tg.configshop.external_api.remnawave.dto.squads.InternalSquadsRootResponse;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveTrafficLimitAndInternalSquadsUpdateRequest;
import tg.configshop.external_api.remnawave.dto.user.RemnaveUserUpdateRequest;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveTrafficLimitUpdateRequest;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserRequest;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserRootResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RemnawaveClientImpl implements RemnawaveClient {
    private final RestClient remnawaveRestClient;

    private final long TRIAL_PERIOD_IN_SECONDS = 432000;
    private final int TRIAL_HWID_DEVICE_LIMIT = 2;

    @Value("${FREE_TRAFFIC_GB}")
    private long freeTrafficGb;

    @Override
    // TODO bring this logic to the service
    public RemnawaveUserResponse createBasicUser(String username, Long telegramId) {
        return remnawaveRestClient.post()
                .uri("/api/users")
                .body(new RemnawaveUserRequest(username, Instant.now().plusSeconds(TRIAL_PERIOD_IN_SECONDS), telegramId, freeTrafficGb * 1024L * 1024 * 1024, TRIAL_HWID_DEVICE_LIMIT, getInternalSquads()))
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
    public RemnawaveUserResponse getUserByUsername(String username) {
        return remnawaveRestClient.get()
                .uri("/api/users/by-username/{username}", username)
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();
    }

    @Override
    public RemnawaveUserResponse updateSubscription(String uuid, Instant expireAt, Integer hwidDeviceLimit) {
        return remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new RemnaveUserUpdateRequest(uuid, expireAt, hwidDeviceLimit))
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();

    }

    @Override
    public RemnawaveUserResponse updateTrafficLimit(String uuid, Long trafficLimitBytes) {
        return remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new RemnawaveTrafficLimitUpdateRequest(uuid, trafficLimitBytes))
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();

    }

    @Override
    public RemnawaveUserResponse resetUserTraffic(String uuid) {
        return remnawaveRestClient.post()
                .uri("/api/users/{uuid}/actions/reset-traffic", uuid)
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();
    }

    @Override
    public RemnawaveUserResponse updateTrafficLimitAndInternalSquads(String uuid, Long trafficLimitBytes, List<String> activeInternalSquads) {
        return remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new RemnawaveTrafficLimitAndInternalSquadsUpdateRequest(uuid, trafficLimitBytes, activeInternalSquads))
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

    @Override
    public void deleteDevice(String uuid, String hwid) {
        remnawaveRestClient.post()
                .uri("/api/hwid/devices/delete")
                .body(new DeleteDeviceRequest(uuid, hwid))
                .retrieve()
                .toBodilessEntity();

    }

    @Override
    public void updateDeviceCount(String uuid, int countDevices) {
        remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new AddDeviceRequest(countDevices, uuid))
                .retrieve()
                .toBodilessEntity();
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
