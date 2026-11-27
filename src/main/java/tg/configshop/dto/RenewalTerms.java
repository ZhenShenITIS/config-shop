package tg.configshop.dto;

import java.time.Instant;

public record RenewalTerms(int days, int deviceLimit, long price, Instant currentExpiresAt) {}
