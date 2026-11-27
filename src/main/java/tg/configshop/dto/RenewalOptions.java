package tg.configshop.dto;

import java.time.Instant;
import java.util.List;

public record RenewalOptions(Instant currentExpiresAt, int deviceLimit, boolean estimated,
                             List<Option> options) {
    public record Option(int days, long price, Instant expiresAt) {}
}
