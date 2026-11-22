package tg.configshop.web.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tg.configshop.model.WebPasswordReset;
import tg.configshop.model.WebRefreshToken;
import tg.configshop.model.WebUser;
import tg.configshop.repositories.WebPasswordResetRepository;
import tg.configshop.repositories.WebRefreshTokenRepository;
import tg.configshop.repositories.WebUserRepository;
import tg.configshop.web.WebTokens;
import tg.configshop.web.registration.WebAuthException;
import tg.configshop.web.registration.WebAuthMailService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WebAuthenticationService {
    private static final Duration ACCESS_TTL = Duration.ofMinutes(10);
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final WebUserRepository users;
    private final WebRefreshTokenRepository refreshTokens;
    private final WebPasswordResetRepository passwordResets;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final WebAuthProperties properties;
    private final WebAuthMailService mail;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String dummyPasswordHash;

    public WebAuthenticationService(WebUserRepository users, WebRefreshTokenRepository refreshTokens,
            WebPasswordResetRepository passwordResets, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            WebAuthProperties properties, WebAuthMailService mail, TransactionTemplate transactions, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordResets = passwordResets;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.mail = mail;
        this.transactions = transactions;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(WebTokens.generate());
    }

    @Transactional
    public Session login(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw invalidCredentials();
        var user = users.findByEmailWithLock(email).orElse(null);
        boolean matches = passwordEncoder.matches(password, user == null ? dummyPasswordHash : user.getPasswordHash());
        if (!matches || user == null || user.getEmailVerifiedAt() == null) throw invalidCredentials();

        Instant now = clock.instant();
        return issueSession(user, UUID.randomUUID(), now, now.plus(REFRESH_TTL), null);
    }

    @Transactional
    public Session refresh(String token) {
        var locked = lockRefresh(token);
        if (locked == null) return null;
        var current = locked.token();
        Instant now = clock.instant();
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) return null;
        if (current.getUsedAt() != null) {
            // Commit the revocation even though the controller will return 401.
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            return null;
        }
        if (locked.user().getEmailVerifiedAt() == null) return null;

        return issueSession(locked.user(), current.getFamilyId(), now, current.getExpiresAt(), current);
    }

    @Transactional
    public void logout(String token) {
        var locked = lockRefresh(token);
        if (locked != null) refreshTokens.revokeFamily(locked.token().getFamilyId(), clock.instant());
    }

    public void requestPasswordReset(String email) {
        ResetDelivery delivery = transactions.execute(status -> {
            var user = users.findByEmailWithLock(email).orElse(null);
            if (user == null || user.getEmailVerifiedAt() == null) return null;

            Instant now = clock.instant();
            var reset = passwordResets.findById(user.getId()).orElseGet(() -> new WebPasswordReset(user.getId()));
            if (reset.getCreatedAt() != null && now.isBefore(reset.getCreatedAt().plusSeconds(60))) return null;

            String token = WebTokens.generate();
            reset.setTokenHash(WebTokens.hash(token));
            reset.setCreatedAt(now);
            reset.setExpiresAt(now.plus(RESET_TTL));
            passwordResets.save(reset);
            return new ResetDelivery(user.getId(), user.getEmail(), token);
        });
        if (delivery != null) mail.sendPasswordReset(delivery.userId(), delivery.email(), delivery.token());
    }

    @Transactional
    public void resetPassword(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_request",
                    "Пароль должен занимать не более 72 байт UTF-8.");
        }
        String hash = WebTokens.hash(token);
        UUID userId = passwordResets.findWebUserIdByTokenHash(hash).orElseThrow(this::invalidResetToken);
        var user = users.findByIdWithLock(userId).orElseThrow(this::invalidResetToken);
        var reset = passwordResets.findById(userId).orElseThrow(this::invalidResetToken);
        Instant now = clock.instant();
        if (!reset.getTokenHash().equals(hash) || !reset.getExpiresAt().isAfter(now)
                || user.getEmailVerifiedAt() == null) {
            throw invalidResetToken();
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUpdatedAt(now);
        passwordResets.delete(reset);
        refreshTokens.revokeForUser(userId, now);
    }

    private LockedRefresh lockRefresh(String token) {
        if (token == null || !token.matches(WebTokens.FORMAT)) return null;
        String hash = WebTokens.hash(token);
        // Select just the ID before locking so the persistence context cannot cache a stale token.
        var user = refreshTokens.findWebUserIdByTokenHash(hash).flatMap(users::findByIdWithLock);
        if (user.isEmpty()) return null;
        return refreshTokens.findByTokenHash(hash).map(current -> new LockedRefresh(user.get(), current)).orElse(null);
    }

    private Session issueSession(WebUser user, UUID familyId, Instant now, Instant expiresAt, WebRefreshToken previous) {
        String token = WebTokens.generate();
        var saved = refreshTokens.save(WebRefreshToken.builder().webUserId(user.getId()).familyId(familyId)
                .tokenHash(WebTokens.hash(token)).createdAt(now).expiresAt(expiresAt).build());
        if (previous != null) {
            previous.setUsedAt(now);
            previous.setReplacedById(saved.getId());
        }

        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).audience(List.of(properties.audience()))
                .subject(user.getId().toString()).claim("account_id", user.getAccountId().toString())
                .issuedAt(now).expiresAt(now.plus(ACCESS_TTL)).id(UUID.randomUUID().toString()).build();
        var accessToken = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new Session(accessToken.getTokenValue(), ACCESS_TTL.toSeconds(), token, Duration.between(now, expiresAt));
    }

    private WebAuthException invalidCredentials() {
        return new WebAuthException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Неверный email или пароль.");
    }

    private WebAuthException invalidResetToken() {
        return new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_reset_token",
                "Ссылка сброса пароля недействительна или срок её действия истёк.");
    }

    public record Session(String accessToken, long expiresIn, String refreshToken, Duration refreshMaxAge) { }
    private record LockedRefresh(WebUser user, WebRefreshToken token) { }
    private record ResetDelivery(UUID userId, String email, String token) { }
}
