package tg.configshop.external_api.remnawave.dto.user;

public record RemnawaveTrafficLimitUpdateRequest(
        String uuid,
        Long trafficLimitBytes
) {
}
