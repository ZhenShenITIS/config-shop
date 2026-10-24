package tg.configshop.dto;
import java.time.Instant;
public record DeviceView(String hwid, String platform, String osVersion, String deviceModel,
                         String userAgent, Instant createdAt, Instant updatedAt) {}
