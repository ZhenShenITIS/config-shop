package tg.configshop.external_api.remnawave.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;


@JsonIgnoreProperties(ignoreUnknown = true)
public record Device(
        String hwid,
        String userUuid,
        Long userId,
        String platform,
        String osVersion,
        String deviceModel,
        String userAgent,
        Instant createdAt,
        Instant updatedAt
) {
}
