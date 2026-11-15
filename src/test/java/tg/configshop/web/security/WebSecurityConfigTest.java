package tg.configshop.web.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class WebSecurityConfigTest {
    private final WebSecurityConfig config = new WebSecurityConfig();

    @Test
    void rejectsMissingMalformedAndShortSecret() {
        for (String secret : new String[]{null, "", "not base64!", Base64.getEncoder().encodeToString(new byte[31])}) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> config.jwtDecoder(new WebAuthProperties(secret, "issuer", "audience")));
            assertTrue(failure.getMessage().contains("WEB_AUTH_JWT_SECRET_BASE64"));
        }
    }

    @Test
    void requiresIssuerAndAudience() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(IllegalArgumentException.class, () -> config.jwtDecoder(new WebAuthProperties(secret, "", "audience")));
        assertThrows(IllegalArgumentException.class, () -> config.jwtDecoder(new WebAuthProperties(secret, "issuer", "")));
    }

    @Test
    void passwordEncoderUsesBcryptAndChecksPasswords() {
        var encoder = config.passwordEncoder();
        String hash = encoder.encode("test-password");
        assertTrue(hash.startsWith("{bcrypt}"));
        assertTrue(encoder.matches("test-password", hash));
        assertFalse(encoder.matches("other-password", hash));
    }
}
