package tg.configshop.web.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

class WebAccessTokenValidator implements OAuth2TokenValidator<Jwt> {
    private static final Duration MAX_LIFETIME = Duration.ofMinutes(15);
    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error("invalid_token");
    private final String audience;

    WebAccessTokenValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        if (!isUuid(jwt.getClaims().get("sub")) || !isUuid(jwt.getClaims().get("account_id"))
                || jwt.getAudience() == null || !jwt.getAudience().contains(audience)
                || issuedAt == null || expiresAt == null
                || !expiresAt.isAfter(issuedAt)
                || expiresAt.isAfter(issuedAt.plus(MAX_LIFETIME))
                || issuedAt.isAfter(Instant.now().plusSeconds(60))) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }

    private boolean isUuid(Object value) {
        if (!(value instanceof String text)) return false;
        try {
            return UUID.fromString(text).toString().equalsIgnoreCase(text);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
