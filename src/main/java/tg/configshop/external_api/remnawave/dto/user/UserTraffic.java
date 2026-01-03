package tg.configshop.external_api.remnawave.dto.user;

public record UserTraffic (
        Long usedTrafficBytes,
        Long lifetimeUsedTrafficBytes
) {
}
