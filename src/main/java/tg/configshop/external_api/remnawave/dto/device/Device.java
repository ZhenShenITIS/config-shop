package tg.configshop.external_api.remnawave.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/*
{
  "response": {
    "total": 1,
    "devices": [
      {
        "hwid": "string",
        "userUuid": "123e4567-e89b-12d3-a456-426614174000",
        "platform": null,
        "osVersion": null,
        "deviceModel": null,
        "userAgent": null,
        "createdAt": "2026-01-02T09:01:49.631Z",
        "updatedAt": "2026-01-02T09:01:49.631Z"
      }
    ]
  }
}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Device(
        String hwid,
        String userUuid,
        String platform,
        String osVersion,
        String deviceModel,
        String userAgent,
        Instant createdAt,
        Instant updatedAt
) {
}
