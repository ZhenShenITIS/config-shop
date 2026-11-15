package tg.configshop.web;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.configshop.alerting.AlertService;
import tg.configshop.external_api.remnawave.webhook.InvalidRemnawaveWebhookSignatureException;
import tg.configshop.external_api.remnawave.webhook.RemnawaveWebhookController;
import tg.configshop.external_api.remnawave.webhook.RemnawaveWebhookVerificationService;
import tg.configshop.model.WebUser;
import tg.configshop.repositories.WebUserRepository;
import tg.configshop.services.ExternalTrafficService;
import tg.configshop.telegram.bot.WebhookBot;
import tg.configshop.telegram.bot.WebhookController;
import tg.configshop.web.security.WebJwtAuthenticationConverter;
import tg.configshop.web.security.WebSecurityConfig;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MeController.class, properties = {
        "web.auth.jwt-secret-base64=" + WebSecurityTest.SECRET_BASE64,
        "web.auth.issuer=config-shop", "web.auth.audience=config-shop-web-api", "start.webhook=true"
})
@Import({WebSecurityConfig.class, WebJwtAuthenticationConverter.class, RemnawaveWebhookController.class,
        WebhookController.class, WebSecurityTest.TestRoutes.class})
@ActiveProfiles("dev")
class WebSecurityTest {
    static final String SECRET_BASE64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    private static final byte[] SECRET = Base64.getDecoder().decode(SECRET_BASE64);
    private static final UUID USER_ID = UUID.fromString("f143f358-e302-4caa-af34-a19bf4e326d8");
    private static final UUID ACCOUNT_ID = UUID.fromString("eb75af1b-955b-4313-a5e4-d58f6425a122");
    private static final Instant VERIFIED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean WebUserRepository users;
    @MockitoBean RemnawaveWebhookVerificationService webhookVerification;
    @MockitoBean ExternalTrafficService traffic;
    @MockitoBean WebhookBot webhookBot;
    @MockitoBean AlertService alerts;

    @BeforeEach
    void user() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(WebUser.builder()
                .id(USER_ID).accountId(ACCOUNT_ID).email("user@example.com")
                .passwordHash("must-not-leak").emailVerifiedAt(VERIFIED_AT).build()));
    }

    @Test
    void returnsOwnProfileWithoutSessionOrPassword() throws Exception {
        var result = mvc.perform(get("/api/me").param("accountId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(validClaims().build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webUserId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.emailVerifiedAt").value(VERIFIED_AT.toString()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(content().string(not(containsString("must-not-leak"))))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test
    void requiresBearerHeaderAndDoesNotAcceptTokenFromQueryOrCookie() throws Exception {
        mvc.perform(get("/api/me").param("access_token", sign(validClaims().build()))
                        .cookie(new jakarta.servlet.http.Cookie("access_token", sign(validClaims().build()))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @MethodSource("invalidClaims")
    void rejectsInvalidClaims(JWTClaimsSet claims) throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(claims)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(users);
    }

    static Stream<JWTClaimsSet> invalidClaims() {
        Instant now = Instant.now();
        return Stream.of(
                validClaims().issuer("other").build(),
                validClaims().issuer(null).build(),
                validClaims().audience("other").build(),
                validClaims().claim("aud", null).build(),
                validClaims().subject("invalid-uuid").build(),
                validClaims().subject(null).build(),
                validClaims().subject("1-1-1-1-1").build(),
                validClaims().claim("account_id", "invalid-uuid").build(),
                validClaims().claim("account_id", null).build(),
                validClaims().claim("account_id", 42).build(),
                validClaims().expirationTime(null).build(),
                validClaims().issueTime(null).build(),
                validClaims().issueTime(Date.from(now.minusSeconds(600)))
                        .expirationTime(Date.from(now.minusSeconds(120))).build(),
                validClaims().notBeforeTime(Date.from(now.plusSeconds(300))).build(),
                validClaims().issueTime(Date.from(now.plusSeconds(300))).build(),
                validClaims().expirationTime(Date.from(now.plusSeconds(1800))).build(),
                validClaims().expirationTime(Date.from(now.minusSeconds(60))).build()
        );
    }

    @Test
    void rejectsUnsignedMalformedWrongSignatureAndWrongAlgorithmTokens() throws Exception {
        var claims = validClaims().build();
        for (String token : new String[]{"not-a-jwt", new PlainJWT(claims).serialize(),
                sign(claims, JWSAlgorithm.HS256, new byte[64]), sign(claims, JWSAlgorithm.HS384, SECRET)}) {
            mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("unauthorized"));
        }
        verifyNoInteractions(users);
    }

    @Test
    void rejectsDeletedUserAndMismatchedAccount() throws Exception {
        when(users.findById(USER_ID)).thenReturn(Optional.empty());
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sign(validClaims().build())))
                .andExpect(status().isUnauthorized());
        user();
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + sign(validClaims().claim("account_id", UUID.randomUUID().toString()).build())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerPostWorksWithoutCsrfAndForbiddenResponseIsJson() throws Exception {
        String token = sign(validClaims().build());
        mvc.perform(post("/api/test/update").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/test/forbidden").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void integrationsDoNotRequireJwtOrCsrfAndRemnawaveStillChecksSignature() throws Exception {
        when(webhookVerification.verifyAndExtractLimitedEvent(any(), eq("valid"))).thenReturn(Optional.empty());
        mvc.perform(post("/internal/remnawave/webhook").contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header("X-Remnawave-Signature", "valid").header(HttpHeaders.AUTHORIZATION, "Bearer invalid"))
                .andExpect(status().isOk());
        when(webhookVerification.verifyAndExtractLimitedEvent(any(), eq("invalid")))
                .thenThrow(new InvalidRemnawaveWebhookSignatureException());
        mvc.perform(post("/internal/remnawave/webhook").content("{}").header("X-Remnawave-Signature", "invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/webhook").contentType(MediaType.APPLICATION_JSON).content("{\"update_id\":1}"))
                .andExpect(status().isOk());
        verify(webhookBot, timeout(1000)).processUpdate(any());
        verifyNoInteractions(users);
    }

    @Test
    void monitoringRemainsAccessibleAndOtherRoutesAreDenied() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/prometheus", "/actuator/info"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/future")).andExpect(status().isUnauthorized());
    }

    private static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder().issuer("config-shop").audience("config-shop-web-api")
                .subject(USER_ID.toString()).claim("account_id", ACCOUNT_ID.toString())
                .issueTime(Date.from(now.minusSeconds(30))).expirationTime(Date.from(now.plusSeconds(600)));
    }

    private static String sign(JWTClaimsSet claims) throws Exception {
        return sign(claims, JWSAlgorithm.HS256, SECRET);
    }

    private static String sign(JWTClaimsSet claims, JWSAlgorithm algorithm, byte[] key) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    @RestController
    static class TestRoutes {
        @PostMapping("/api/test/update")
        Map<String, String> update() { return Map.of("result", "ok"); }

        @GetMapping("/api/test/forbidden")
        void forbidden() { throw new AccessDeniedException("forbidden"); }

        @GetMapping({"/actuator/health", "/actuator/prometheus", "/actuator/info"})
        Map<String, String> monitoring() { return Map.of("status", "UP"); }
    }
}
