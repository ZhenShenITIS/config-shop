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
import tg.configshop.web.WebTokens;
import tg.configshop.web.dto.ConfirmEmailResponse;
import tg.configshop.web.dto.RegisterRequest;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class WebRegistrationService {
    private final WebUserRepository users;
    private final EmailConfirmationRepository confirmations;
    private final PromoCodeRepository promoCodes;
    private final RemnawaveSubscriptionRepository subscriptions;
    private final AccountService accounts;
    private final ReferralService referrals;
    private final RegistrationService registration;
    private final PasswordEncoder passwordEncoder;
    private final WebAuthMailService mail;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public void register(RegisterRequest request) {
        String passwordHash = encodePassword(request.password());
        Delivery delivery;
        try {
            delivery = transactions.execute(status -> {
                if (users.findByEmailIgnoreCase(request.email()).isPresent()) throw WebAuthException.emailTaken();
                UUID referrerId = resolveReferrer(request.referralCode());
                var user = users.saveAndFlush(WebUser.builder().accountId(accounts.createAccount())
                        .email(request.email()).passwordHash(passwordHash).createdAt(clock.instant())
                        .updatedAt(clock.instant()).build());
                return issueConfirmation(user, referrerId, clock.instant());
            });
        } catch (DataIntegrityViolationException e) {
            if (users.findByEmailIgnoreCase(request.email()).isPresent()) throw WebAuthException.emailTaken();
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
                throw WebAuthException.confirmationCooldown();
            }
            UUID referrerId = previous.map(EmailConfirmation::getReferrerAccountId).orElse(null);
            confirmations.deleteForUser(user.getId());
            return issueConfirmation(user, referrerId, now);
        });
        if (delivery != null) mail.send(delivery.webUserId(), delivery.email(), delivery.token());
    }

    public ConfirmEmailResponse confirm(String token) {
        String tokenHash = WebTokens.hash(token);
        UUID userId = confirmations.findWebUserIdByTokenHash(tokenHash)
                .orElseThrow(WebAuthException::invalidConfirmationToken);
        Confirmation result = transactions.execute(status -> {
            var user = users.findByIdWithLock(userId).orElseThrow(WebAuthException::invalidConfirmationToken);
            // Resend may have replaced this token while the request waited for the user lock.
            var confirmation = confirmations.findByTokenHash(tokenHash)
                    .orElseThrow(WebAuthException::invalidConfirmationToken);
            Instant now = clock.instant();
            if (!confirmation.getExpiresAt().isAfter(now)) throw WebAuthException.invalidConfirmationToken();
            if (user.getEmailVerifiedAt() != null) {
                return new Confirmation(user.getId(), user.getAccountId(), false);
            }
            if (confirmation.getUsedAt() != null) throw WebAuthException.invalidConfirmationToken();
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
        String token = WebTokens.generate();
        confirmations.saveAndFlush(EmailConfirmation.builder().webUserId(user.getId()).tokenHash(WebTokens.hash(token))
                .referrerAccountId(referrerId).createdAt(now).expiresAt(now.plus(Duration.ofHours(24))).build());
        return new Delivery(user.getId(), user.getEmail(), token);
    }

    private String encodePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_request",
                    "Пароль должен занимать не более 72 байт UTF-8.");
        }
        return passwordEncoder.encode(password);
    }

    private WebAuthException invalidReferral() {
        return new WebAuthException(HttpStatus.BAD_REQUEST, "invalid_referral_code",
                "Реферальный код не найден.");
    }

    private record Delivery(UUID webUserId, String email, String token) { }
    private record Confirmation(UUID webUserId, UUID accountId, boolean firstConfirmation) { }
}
