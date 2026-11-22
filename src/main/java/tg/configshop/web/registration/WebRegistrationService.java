package tg.configshop.web.registration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tg.configshop.model.EmailConfirmation;
import tg.configshop.model.WebUser;
import tg.configshop.repositories.EmailConfirmationRepository;
import tg.configshop.repositories.PromoCodeRepository;
import tg.configshop.repositories.RemnawaveSubscriptionRepository;
import tg.configshop.repositories.WebUserRepository;
import tg.configshop.services.AccountService;
import tg.configshop.services.ReferralService;
import tg.configshop.services.RegistrationService;
import tg.configshop.web.dto.ConfirmEmailResponse;
import tg.configshop.web.dto.RegisterRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class WebRegistrationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final WebUserRepository users;
    private final EmailConfirmationRepository confirmations;
    private final PromoCodeRepository promoCodes;
    private final RemnawaveSubscriptionRepository subscriptions;
    private final AccountService accounts;
    private final ReferralService referrals;
    private final RegistrationService registration;
    private final PasswordEncoder passwordEncoder;
    private final EmailConfirmationMailService mail;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public void register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new WebRegistrationException(HttpStatus.BAD_REQUEST, "invalid_request",
                    "Пароль должен занимать не более 72 байт UTF-8.");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        Delivery delivery;
        try {
            delivery = transactions.execute(status -> {
                if (users.findByEmailIgnoreCase(request.email()).isPresent()) throw emailTaken();
                UUID referrerId = resolveReferrer(request.referralCode());
                var user = users.saveAndFlush(WebUser.builder().accountId(accounts.createAccount())
                        .email(request.email()).passwordHash(passwordHash).createdAt(clock.instant())
                        .updatedAt(clock.instant()).build());
                return issueConfirmation(user, referrerId, clock.instant());
            });
        } catch (DataIntegrityViolationException e) {
            if (users.findByEmailIgnoreCase(request.email()).isPresent()) throw emailTaken();
            throw e;
        }
        mail.send(delivery.webUserId(), delivery.email(), delivery.token());
    }

    public void resend(String email) {
        Delivery delivery = transactions.execute(status -> {
            var found = users.findByEmailWithLock(email);
            if (found.isEmpty()) return null;
            var user = found.get();
            if (user.getEmailVerifiedAt() != null) return null;
            var previous = confirmations.findFirstByWebUserIdOrderByCreatedAtDescIdDesc(user.getId());
            Instant now = clock.instant();
            if (previous.isPresent() && now.isBefore(previous.get().getCreatedAt().plusSeconds(60))) {
                throw new WebRegistrationException(HttpStatus.TOO_MANY_REQUESTS, "confirmation_cooldown",
                        "Повторное письмо можно запросить через минуту.");
            }
            UUID referrerId = previous.map(EmailConfirmation::getReferrerAccountId).orElse(null);
            confirmations.deleteForUser(user.getId());
            return issueConfirmation(user, referrerId, now);
        });
        if (delivery != null) mail.send(delivery.webUserId(), delivery.email(), delivery.token());
    }

    public ConfirmEmailResponse confirm(String token) {
        String tokenHash = hash(token);
        var found = confirmations.findByTokenHash(tokenHash).orElseThrow(this::invalidToken);
        Confirmation result = transactions.execute(status -> {
            var user = users.findByIdWithLock(found.getWebUserId()).orElseThrow(this::invalidToken);
            // Resend may have replaced this token while the request waited for the user lock.
            var confirmation = confirmations.findByTokenHash(tokenHash).orElseThrow(this::invalidToken);
            Instant now = clock.instant();
            if (!confirmation.getExpiresAt().isAfter(now)) throw invalidToken();
            if (user.getEmailVerifiedAt() != null) return new Confirmation(user.getId(), user.getAccountId(), false);
            if (confirmation.getUsedAt() != null) throw invalidToken();
            user.setEmailVerifiedAt(now);
            user.setUpdatedAt(now);
            confirmation.setUsedAt(now);
            if (confirmation.getReferrerAccountId() != null) {
                referrals.createReferral(confirmation.getReferrerAccountId(), user.getAccountId());
            }
            referrals.createReferralCode(user.getAccountId());
            return new Confirmation(user.getId(), user.getAccountId(), true);
        });

        boolean trialCreated;
        if (result.firstConfirmation()) {
            try {
                registration.createTrialSubscription(result.accountId());
                trialCreated = true;
            } catch (RuntimeException e) {
                log.error("Trial subscription creation failed for account {}, web user {}",
                        result.accountId(), result.webUserId(), e);
                trialCreated = false;
            }
        } else {
            trialCreated = subscriptions.existsByAccount_Id(result.accountId());
        }
        return new ConfirmEmailResponse(true, trialCreated, result.accountId(), trialCreated
                ? "Почта подтверждена. Пробная подписка создана."
                : "Почта подтверждена, но выдачу пробной подписки не удалось завершить. Обратитесь в поддержку и укажите accountId.");
    }

    private UUID resolveReferrer(String code) {
        if (code == null || code.isBlank()) return null;
        var promo = promoCodes.findByCodeIgnoreCase(code).orElseThrow(this::invalidReferral);
        if (!Boolean.TRUE.equals(promo.getIsReferral()) || promo.getReferrer() == null) throw invalidReferral();
        return promo.getReferrer().getId();
    }

    private Delivery issueConfirmation(WebUser user, UUID referrerId, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        confirmations.saveAndFlush(EmailConfirmation.builder().webUserId(user.getId()).tokenHash(hash(token))
                .referrerAccountId(referrerId).createdAt(now).expiresAt(now.plus(Duration.ofHours(24))).build());
        return new Delivery(user.getId(), user.getEmail(), token);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private WebRegistrationException emailTaken() {
        return new WebRegistrationException(HttpStatus.CONFLICT, "email_already_registered",
                "Этот email уже зарегистрирован.");
    }

    private WebRegistrationException invalidToken() {
        return new WebRegistrationException(HttpStatus.BAD_REQUEST, "invalid_confirmation_token",
                "Ссылка подтверждения недействительна или срок её действия истёк.");
    }

    private WebRegistrationException invalidReferral() {
        return new WebRegistrationException(HttpStatus.BAD_REQUEST, "invalid_referral_code",
                "Реферальный код не найден.");
    }

    private record Delivery(UUID webUserId, String email, String token) { }
    private record Confirmation(UUID webUserId, UUID accountId, boolean firstConfirmation) { }
}
