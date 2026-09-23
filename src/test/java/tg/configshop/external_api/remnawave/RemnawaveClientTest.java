package tg.configshop.external_api.remnawave;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.external_api.remnawave.config.RemnawaveConfig;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RemnawaveClientTest {
    private static final String BASE = "https://panel.test";
    private static final RemnawaveUserRef USER = new RemnawaveUserRef("old-user-uuid", 9876543210L);
    private static final Instant EXPIRES = Instant.parse("2026-10-01T00:00:00Z");
    private final ObjectMapper json = new ObjectMapper();
    private MockRestServiceServer server;
    private RemnawaveClientImpl client;

    private void setup(int version) {
        RestClient.Builder builder = new RemnawaveConfig().remnawaveRestClient(BASE, "test-token").mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RemnawaveClientImpl(builder.build(), new RemnawaveApiVersion(version));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void readsUserAndResetsTrafficUsingSelectedPath(int version) {
        setup(version);
        String identifier = version == 2 ? USER.uuid() : USER.id().toString();
        expect(HttpMethod.GET, "/api/users/" + identifier).andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));
        expect(HttpMethod.POST, "/api/users/" + identifier + "/actions/reset-traffic")
                .andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));

        var user = client.getUser(USER);
        assertEquals(USER.id(), user.id());
        assertEquals(version == 2 ? USER.uuid() : null, user.uuid());
        assertEquals(EXPIRES, user.expireAt());
        assertEquals(USER.id(), client.resetUserTraffic(USER).id());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void patchBodiesContainExactlyOneIdentifier(int version) {
        setup(version);
        String identity = version == 2 ? "\"uuid\":\"old-user-uuid\"" : "\"id\":9876543210";
        expectJson(HttpMethod.PATCH, "/api/users", "{" + identity + ",\"expireAt\":\"2026-10-01T00:00:00Z\",\"hwidDeviceLimit\":3}")
                .andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));
        expectJson(HttpMethod.PATCH, "/api/users", "{" + identity + ",\"trafficLimitBytes\":1000}")
                .andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));
        expectJson(HttpMethod.PATCH, "/api/users", "{" + identity + ",\"trafficLimitBytes\":2000,\"activeInternalSquads\":[\"squad-uuid\"]}")
                .andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));
        expectJson(HttpMethod.PATCH, "/api/users", "{" + identity + ",\"hwidDeviceLimit\":4}")
                .andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));

        client.updateSubscription(USER, EXPIRES, 3);
        client.updateTrafficLimit(USER, 1000L);
        client.updateTrafficLimitAndInternalSquads(USER, 2000L, List.of("squad-uuid"));
        client.updateDeviceCount(USER, 4);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void hwidRoutesAndBodiesUseUserIdOnlyInV3(int version) {
        setup(version);
        String identifier = version == 2 ? USER.uuid() : USER.id().toString();
        String identity = version == 2 ? "\"userUuid\":\"old-user-uuid\"" : "\"userId\":9876543210";
        expect(HttpMethod.GET, "/api/hwid/devices/" + identifier)
                .andRespond(withSuccess("{\"response\":{\"total\":1,\"devices\":[{\"hwid\":\"device-hwid\"," + identity
                        + ",\"requestIp\":\"127.0.0.1\",\"createdAt\":\"2026-10-01T00:00:00Z\"}]}}", MediaType.APPLICATION_JSON));
        expectJson(HttpMethod.POST, "/api/hwid/devices/delete", "{" + identity + ",\"hwid\":\"device-hwid\"}")
                .andRespond(withSuccess("{\"response\":{\"total\":0,\"devices\":[]}}", MediaType.APPLICATION_JSON));

        var device = client.getUserDevices(USER).getFirst();
        assertEquals(version == 2 ? USER.uuid() : null, device.userUuid());
        assertEquals(version == 3 ? USER.id() : null, device.userId());
        assertEquals(EXPIRES, device.createdAt());
        client.deleteDevice(USER, "device-hwid");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void creationAndLookupByUsernameKeepTheirContract(int version) {
        setup(version);
        ReflectionTestUtils.setField(client, "freeTrafficGb", 10L);
        expect(HttpMethod.GET, "/api/internal-squads")
                .andRespond(withSuccess("{\"response\":{\"total\":2,\"internalSquads\":[{\"uuid\":\"keep-uuid\",\"name\":\"main\"},{\"uuid\":\"skip-uuid\",\"name\":\"traf-serv\"}]}}", MediaType.APPLICATION_JSON));
        expect(HttpMethod.POST, "/api/users").andExpect(request -> {
            var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
            assertEquals(6, body.size());
            assertEquals("736", body.get("username").asText());
            assertEquals(736L, body.get("telegramId").longValue());
            assertTrue(body.get("telegramId").isIntegralNumber());
            assertEquals(10L * 1024 * 1024 * 1024, body.get("trafficLimitBytes").longValue());
            assertEquals(2, body.get("hwidDeviceLimit").intValue());
            assertEquals(json.readTree("[\"keep-uuid\"]"), body.get("activeInternalSquads"));
            assertTrue(Instant.parse(body.get("expireAt").asText()).isAfter(Instant.now()));
        }).andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));
        expect(HttpMethod.GET, "/api/users/by-username/736")
                .andRespond(withSuccess(response(version), MediaType.APPLICATION_JSON));
        assertEquals(USER.id(), client.createBasicUser("736", 736L).id());
        assertEquals(USER.id(), client.getUserByUsername("736").id());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void httpErrorDoesNotTriggerFallback(int version) {
        setup(version);
        String identifier = version == 2 ? USER.uuid() : USER.id().toString();
        expect(HttpMethod.GET, "/api/users/" + identifier).andRespond(withStatus(HttpStatus.NOT_FOUND));
        expect(HttpMethod.POST, "/api/users/" + identifier + "/actions/reset-traffic")
                .andRespond(withServerError());
        assertThrows(RestClientResponseException.class, () -> client.getUser(USER));
        assertThrows(RestClientResponseException.class, () -> client.resetUserTraffic(USER));
        server.verify();
    }

    @Test
    void rejectsMissingIdentifierBeforeSendingRequest() {
        setup(3);
        RemnawaveUserRef missingId = new RemnawaveUserRef(USER.uuid(), null);
        assertThrows(IllegalArgumentException.class, () -> client.getUser(missingId));
        assertThrows(IllegalArgumentException.class, () -> client.updateDeviceCount(missingId, 3));
        assertThrows(IllegalArgumentException.class, () -> client.deleteDevice(missingId, "hwid"));
        server.verify();
        setup(2);
        assertThrows(IllegalArgumentException.class, () -> client.getUser(new RemnawaveUserRef(null, USER.id())));
        server.verify();
    }

    @Test
    void rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException.class, () -> new RemnawaveApiVersion(4));
    }

    private ResponseActions expect(HttpMethod method, String path) {
        return server.expect(requestTo(BASE + path)).andExpect(method(method))
                .andExpect(header("Authorization", "Bearer test-token"));
    }

    private ResponseActions expectJson(HttpMethod method, String path, String expected) {
        return expect(method, path).andExpect(request ->
                assertEquals(json.readTree(expected), json.readTree(((MockClientHttpRequest) request).getBodyAsString())));
    }

    private String response(int version) {
        return "{\"response\":{\"id\":9876543210," + (version == 2 ? "\"uuid\":\"old-user-uuid\"," : "")
                + "\"username\":\"736\",\"telegramId\":736,\"shortUuid\":\"short\","
                + "\"expireAt\":\"2026-10-01T00:00:00Z\",\"subscriptionUrl\":\"https://subscription.test/short\","
                + "\"userTraffic\":{\"usedTrafficBytes\":10,\"lifetimeUsedTrafficBytes\":20},\"extra\":\"ignored\"}}";
    }
}
