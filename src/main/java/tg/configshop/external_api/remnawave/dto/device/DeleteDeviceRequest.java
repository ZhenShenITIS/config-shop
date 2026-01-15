package tg.configshop.external_api.remnawave.dto.device;

public record DeleteDeviceRequest(
        String userUuid,
        String hwid
) {
}
