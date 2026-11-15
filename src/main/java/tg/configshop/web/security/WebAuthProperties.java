package tg.configshop.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("web.auth")
public record WebAuthProperties(String jwtSecretBase64, String issuer, String audience) {
}
