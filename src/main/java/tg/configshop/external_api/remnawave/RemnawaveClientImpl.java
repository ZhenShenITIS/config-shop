package tg.configshop.external_api.remnawave;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
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
    private final RemnawaveApiVersion apiVersion;

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
    public RemnawaveUserResponse getUser(RemnawaveUserRef user) {
        return remnawaveRestClient.get()
                .uri("/api/users/{identifier}", apiVersion.identifier(user))
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
    public RemnawaveUserResponse updateSubscription(RemnawaveUserRef user, Instant expireAt, Integer hwidDeviceLimit) {
        RemnawaveUserRef selected = apiVersion.select(user);
        return remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new RemnaveUserUpdateRequest(selected.uuid(), selected.id(), expireAt, hwidDeviceLimit))
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();

    }

    @Override
    public RemnawaveUserResponse updateTrafficLimit(RemnawaveUserRef user, Long trafficLimitBytes) {
        RemnawaveUserRef selected = apiVersion.select(user);
        return remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new RemnawaveTrafficLimitUpdateRequest(selected.uuid(), selected.id(), trafficLimitBytes))
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();

    }

    @Override
    public RemnawaveUserResponse resetUserTraffic(RemnawaveUserRef user) {
        return remnawaveRestClient.post()
                .uri("/api/users/{identifier}/actions/reset-traffic", apiVersion.identifier(user))
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();
    }

    @Override
    public RemnawaveUserResponse updateTrafficLimitAndInternalSquads(RemnawaveUserRef user, Long trafficLimitBytes, List<String> activeInternalSquads) {
        RemnawaveUserRef selected = apiVersion.select(user);
        return remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new RemnawaveTrafficLimitAndInternalSquadsUpdateRequest(selected.uuid(), selected.id(), trafficLimitBytes, activeInternalSquads))
                .retrieve()
                .body(RemnawaveUserRootResponse.class)
                .response();
    }

    @Override
    public List<Device> getUserDevices(RemnawaveUserRef user) {
        return remnawaveRestClient.get()
                .uri("/api/hwid/devices/{identifier}", apiVersion.identifier(user))
                .retrieve()
                .body(DeviceRootResponse.class)
                .response()
                .devices();
    }

    @Override
    public void deleteDevice(RemnawaveUserRef user, String hwid) {
        RemnawaveUserRef selected = apiVersion.select(user);
        remnawaveRestClient.post()
                .uri("/api/hwid/devices/delete")
                .body(new DeleteDeviceRequest(selected.uuid(), selected.id(), hwid))
                .retrieve()
                .toBodilessEntity();

    }

    @Override
    public void updateDeviceCount(RemnawaveUserRef user, int countDevices) {
        RemnawaveUserRef selected = apiVersion.select(user);
        remnawaveRestClient.patch()
                .uri("/api/users")
                .body(new AddDeviceRequest(countDevices, selected.uuid(), selected.id()))
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
