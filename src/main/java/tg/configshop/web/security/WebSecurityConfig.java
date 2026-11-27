package tg.configshop.web.security;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.util.Assert;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(WebAuthProperties.class)
public class WebSecurityConfig {
    private static final AuthenticationEntryPoint UNAUTHORIZED = (request, response, exception) -> {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writeError(response, 401, "unauthorized");
    };
    private static final AccessDeniedHandler FORBIDDEN = (request, response, exception) ->
            writeError(response, 403, "forbidden");

    @Bean
    SecretKeySpec jwtSigningKey(WebAuthProperties properties) {
        Assert.hasText(properties.issuer(), "WEB_AUTH_ISSUER must not be blank");
        Assert.hasText(properties.audience(), "WEB_AUTH_AUDIENCE must not be blank");
        byte[] key;
        try {
            key = Base64.getDecoder().decode(properties.jwtSecretBase64() == null ? "" : properties.jwtSecretBase64());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("WEB_AUTH_JWT_SECRET_BASE64 must contain valid Base64");
        }
        Assert.isTrue(key.length >= 32, "WEB_AUTH_JWT_SECRET_BASE64 must encode at least 32 random bytes");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(WebAuthProperties properties, SecretKeySpec jwtSigningKey) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new WebAccessTokenValidator(properties.audience())));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    @Order(1)
    SecurityFilterChain webApiSecurity(HttpSecurity http, JwtDecoder decoder,
                                       WebJwtAuthenticationConverter converter) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        var cookieEndpoints = new OrRequestMatcher(paths.matcher(HttpMethod.POST, "/api/auth/refresh"),
                paths.matcher(HttpMethod.POST, "/api/auth/logout"));
        var bearer = new BearerTokenAuthenticationConverter();
        return http.securityMatcher("/api/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/confirm-email",
                                "/api/auth/resend-confirmation", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/webhooks/platega").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(UNAUTHORIZED).accessDeniedHandler(FORBIDDEN))
                .oauth2ResourceServer(resource -> resource
                        .authenticationEntryPoint(UNAUTHORIZED).accessDeniedHandler(FORBIDDEN)
                        // Clients can refresh or log out even with an expired access token in the header.
                        .authenticationConverter(request -> cookieEndpoints.matches(request) ? null : bearer.convert(request))
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    SecurityFilterChain integrationSecurity(HttpSecurity http) throws Exception {
        return http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/webhook", "/internal/remnawave/webhook"))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhook", "/internal/remnawave/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus", "/actuator/info").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(UNAUTHORIZED).accessDeniedHandler(FORBIDDEN))
                .build();
    }

    private static void writeError(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
