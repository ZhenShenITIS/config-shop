package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import tg.configshop.constants.PaymentResult;
import tg.configshop.constants.TopUpSource;
import tg.configshop.dto.PaymentLimits;
import tg.configshop.dto.PaymentView;
import tg.configshop.dto.PlategaCallback;
import tg.configshop.dto.TopUpView;
import tg.configshop.events.PaymentConfirmedEvent;
import tg.configshop.external_api.pay.PlategaClient;
import tg.configshop.external_api.pay.constants.PaymentMethod;
import tg.configshop.external_api.pay.constants.PaymentStatus;
import tg.configshop.external_api.pay.dto.CreatePaymentRequest;
import tg.configshop.external_api.pay.dto.PaymentDetails;
import tg.configshop.external_api.pay.dto.PaymentStatusResponse;
import tg.configshop.external_api.pay.model.PlategaPayment;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.BalanceService;
import tg.configshop.services.PaymentService;
import tg.configshop.services.PlategaCallbackService;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService, PlategaCallbackService {
    private static final String CURRENCY = "RUB";
    private static final String PAYLOAD_PREFIX = "config-shop:";
    private final PlategaClient plategaClient;
    private final PlategaPaymentRepository payments;
    private final AccountRepository accounts;
    private final BalanceService balanceService;
    private final TopUpRepository topUps;
    private final ApplicationEventPublisher events;
    private final PlatformTransactionManager transactionManager;
    private volatile Instant retryStatusAfter = Instant.MIN;

    @Override
    public PaymentLimits limits() {
        return new PaymentLimits(100, 10000);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentView createPlategaPayment(Long amount, int method, UUID accountId, String returnUrl, String failedUrl) {
        var payment = createTopUp(UUID.randomUUID(), amount, method, accountId, accountId + "@mail.ru", returnUrl, failedUrl);
        if (payment.redirect() == null) throw new IllegalStateException("Platega did not return a payment link");
        return payment;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentView createTopUp(UUID requestId, Long amount, int methodCode, UUID accountId, String buyerEmail,
                                   String returnUrl, String failedUrl) {
        if (requestId == null || amount == null || amount < limits().minimum() || amount > limits().maximum()) {
            throw new IllegalArgumentException("Payment amount or request ID is invalid");
        }
        var method = PaymentMethod.fromIntMethod(methodCode);
        validateHttps(returnUrl);
        validateHttps(failedUrl);

        var transaction = transaction();
        boolean reserved = Boolean.TRUE.equals(transaction.execute(tx -> {
            accounts.findByIdWithLock(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
            return payments.reserve(accountId, requestId, amount, method.getTextMethod()) == 1;
        }));
        if (!reserved) {
            return readOnlyTransaction().execute(tx -> {
                var payment = payments.findByRequestIdAndAccount_Id(requestId, accountId)
                        .orElseThrow(() -> new IllegalArgumentException("Payment request ID is unavailable"));
                if (payment.getAmount().longValueExact() != amount || payment.getPaymentMethod() != method) {
                    throw new IllegalArgumentException("Payment request parameters changed");
                }
                return paymentView(payment);
            });
        }

        String providerId;
        String redirect;
        PaymentStatus providerStatus;
        try {
            var response = plategaClient.createPayment(new CreatePaymentRequest(
                    methodCode, new PaymentDetails(amount.doubleValue(), CURRENCY), "Пополнение баланса",
                    returnUrl, failedUrl, PAYLOAD_PREFIX + requestId, new CreatePaymentRequest.Metadata(buyerEmail)));
            var details = response.paymentDetails().trim().split("\\s+");
            validateHttps(response.redirect());
            if (response.transactionId() == null || response.transactionId().isBlank()
                    || response.status() == null || response.status().isCreationState()
                    || details.length != 2 || !CURRENCY.equals(details[1]) || response.paymentMethod() != method
                    || new BigDecimal(details[0]).compareTo(BigDecimal.valueOf(amount)) != 0) {
                throw new IllegalStateException("Invalid Platega creation response");
            }
            providerId = response.transactionId();
            providerStatus = response.status();
            redirect = response.redirect();
        } catch (RuntimeException exception) {
            log.warn("Platega creation failed for request {} ({})", requestId, exception.getClass().getSimpleName());
            boolean rejected = exception instanceof HttpClientErrorException error
                    && switch (error.getStatusCode().value()) {
                        case 400, 401, 403, 422 -> true;
                        default -> false;
                    };
            return transaction.execute(tx -> {
                var payment = lockByRequest(requestId);
                if (payment.getStatus() == PaymentStatus.CREATING) {
                    setStatus(payment, rejected ? PaymentStatus.CREATION_FAILED : PaymentStatus.CREATION_UNCERTAIN);
                }
                return paymentView(payment);
            });
        }

        return transaction.execute(tx -> {
            var payment = lockByRequest(requestId);
            if (payment.getTransactionId() == null) {
                payment.setTransactionId(providerId);
                setStatus(payment, PaymentStatus.PENDING);
                applyStatus(payment, providerStatus);
            } else if (!payment.getTransactionId().equals(providerId)) {
                throw new IllegalStateException("Platega returned a different ID for payment request " + requestId);
            }
            payment.setRedirect(redirect);
            return paymentView(payment);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TopUpView> getTopUp(UUID requestId, UUID accountId) {
        return payments.findByRequestIdAndAccount_Id(requestId, accountId)
                .map(payment -> new TopUpView(requestId, payment.getAmount().longValueExact(),
                        switch (payment.getStatus()) {
                            case CREATING -> payment.getCreatedAt().isAfter(Instant.now().minusSeconds(60))
                                    ? TopUpView.State.CREATING : TopUpView.State.MANUAL_REVIEW;
                            case PENDING -> TopUpView.State.PENDING;
                            case CONFIRMED -> result(payment) == PaymentResult.CONFIRMED
                                    ? TopUpView.State.CONFIRMED : TopUpView.State.MANUAL_REVIEW;
                            case CANCELED -> TopUpView.State.CANCELED;
                            case CREATION_FAILED -> TopUpView.State.FAILED;
                            default -> TopUpView.State.MANUAL_REVIEW;
                        }, payment.getRedirect()));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResult checkPayment(String providerId, UUID accountId) {
        var snapshot = payments.findForCheck(providerId).orElse(null);
        if (snapshot == null) return PaymentResult.MANUAL_REVIEW;
        if (!snapshot.getAccountId().equals(accountId)) {
            throw new IllegalArgumentException("Payment belongs to another account");
        }
        var current = readOnlyTransaction().execute(tx -> payments.findById(snapshot.getId())
                .map(this::result).orElse(PaymentResult.MANUAL_REVIEW));
        if (current != PaymentResult.PROCESSING && snapshot.getStatus() != PaymentStatus.DISPUTED) return current;

        var checked = fetchStatus(providerId);
        if (checked == null) return current;
        var response = checked.response();
        return transaction().execute(tx -> {
            var payment = lockByProvider(providerId);
            if (!payment.getAccount().getId().equals(accountId)) {
                throw new IllegalArgumentException("Payment belongs to another account");
            }
            if (!snapshot.getUpdatedAt().equals(payment.getUpdatedAt())) return result(payment);
            if (!matches(payment, response)) {
                log.error("Invalid status response for Platega payment {}", providerId);
                return PaymentResult.MANUAL_REVIEW;
            }
            return applyStatus(payment, response.status());
        });
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void applyCallback(PlategaCallback callback) {
        PaymentStatus status = switch (callback.status()) {
            case "CONFIRMED" -> PaymentStatus.CONFIRMED;
            case "CANCELED" -> PaymentStatus.CANCELED;
            case "CHARGEBACKED" -> PaymentStatus.CHARGEBACKED;
            case "PARTIALLY_REFUNDED" -> PaymentStatus.PARTIALLY_REFUNDED;
            case "DISPUTED" -> PaymentStatus.DISPUTED;
            default -> null;
        };
        if (status == null) {
            log.info("Ignoring unsupported Platega callback for {}", callback.providerPaymentId());
            return;
        }
        var snapshot = payments.findForCheck(callback.providerPaymentId()).orElse(null);
        var requestId = requestId(callback.payload());
        if (snapshot == null && (requestId == null || payments.findOwnerByRequestId(requestId).isEmpty())) {
            log.info("Ignoring callback for unknown Platega payment {}", callback.providerPaymentId());
            return;
        }

        // A confirmation for a disputed payment can be either a late original callback or a resolved dispute.
        var checked = snapshot != null && snapshot.getStatus() == PaymentStatus.DISPUTED
                && status == PaymentStatus.CONFIRMED ? fetchStatus(callback.providerPaymentId()) : null;
        transaction().executeWithoutResult(tx -> {
            var payment = snapshot != null ? lockByProvider(callback.providerPaymentId()) : lockByRequest(requestId);
            if (payment.getAmount().compareTo(callback.amount()) != 0 || !payment.getCurrency().equals(callback.currency())
                    || !CURRENCY.equals(callback.currency()) || payment.getPaymentMethod().getIntMethod() != callback.paymentMethod()
                    || (payment.getRequestId() != null && !payment.getRequestId().equals(requestId))
                    || (payment.getTransactionId() != null && !payment.getTransactionId().equals(callback.providerPaymentId()))) {
                log.warn("Platega callback did not match stored payment {}", payment.getId());
                throw new IllegalArgumentException("Callback payment details do not match");
            }
            if (payment.getStatus() == PaymentStatus.DISPUTED && status == PaymentStatus.CONFIRMED) {
                if (checked == null || !snapshot.getUpdatedAt().equals(payment.getUpdatedAt())
                        || !matches(payment, checked.response())) {
                    throw new IllegalStateException("Platega dispute confirmation needs reconciliation");
                }
                applyStatus(payment, checked.response().status());
                return;
            }
            if (payment.getTransactionId() == null) {
                payment.setTransactionId(callback.providerPaymentId());
                setStatus(payment, PaymentStatus.PENDING);
            }
            applyStatus(payment, status);
        });
    }

    private ProviderCheck fetchStatus(String providerId) {
        if (Instant.now().isBefore(retryStatusAfter)) return null;
        try {
            return new ProviderCheck(plategaClient.getPayment(providerId));
        } catch (HttpClientErrorException.TooManyRequests error) {
            retryStatusAfter = retryAfter(error.getResponseHeaders());
            log.warn("Platega status checks paused until {}", retryStatusAfter);
        } catch (RuntimeException error) {
            log.warn("Could not fetch Platega payment {} ({})", providerId, error.getClass().getSimpleName());
        }
        return null;
    }

    private record ProviderCheck(PaymentStatusResponse response) {}

    private static boolean matches(PlategaPayment payment, PaymentStatusResponse response) {
        return response != null && response.status() != null && !response.status().isCreationState()
                && payment.getTransactionId().equals(response.id()) && response.paymentDetails() != null
                && response.paymentDetails().amount() != null
                && payment.getAmount().compareTo(response.paymentDetails().amount()) == 0
                && payment.getCurrency().equals(response.paymentDetails().currency()) && CURRENCY.equals(payment.getCurrency());
    }

    private PaymentResult applyStatus(PlategaPayment payment, PaymentStatus status) {
        var old = payment.getStatus();
        if (status == PaymentStatus.PENDING || old == PaymentStatus.CHARGEBACKED
                || (old == PaymentStatus.PARTIALLY_REFUNDED && status != PaymentStatus.CHARGEBACKED)
                || (status == PaymentStatus.CANCELED && (old == PaymentStatus.CONFIRMED || old.requiresReview()
                    || topUps.existsByPlatega_Id(payment.getId())))) {
            return result(payment);
        }
        if (status != old) {
            setStatus(payment, status);
            if (status.requiresReview()) log.warn("Platega payment {} requires manual review ({})", payment.getId(), status);
        }
        if (status == PaymentStatus.CONFIRMED && !topUps.existsByPlatega_Id(payment.getId())) {
            var account = payment.getAccount();
            long amount = payment.getAmount().longValueExact();
            balanceService.credit(TopUp.builder().account(account).value(amount).topUpSource(TopUpSource.EXTERNAL)
                    .platega(payment).build());
            events.publishEvent(new PaymentConfirmedEvent(account.getId(), amount, payment.getId()));
            return PaymentResult.CONFIRMED;
        }
        return result(payment);
    }

    private PaymentResult result(PlategaPayment payment) {
        var status = payment.getStatus();
        if (status.requiresReview()) return PaymentResult.MANUAL_REVIEW;
        if (topUps.existsByPlatega_Id(payment.getId())) {
            return status == PaymentStatus.CANCELED ? PaymentResult.MANUAL_REVIEW : PaymentResult.CONFIRMED;
        }
        return switch (status) {
            case PENDING, CREATING -> PaymentResult.PROCESSING;
            case CANCELED -> PaymentResult.CANCELED;
            default -> PaymentResult.MANUAL_REVIEW;
        };
    }

    private PlategaPayment lockByProvider(String providerId) {
        var owner = payments.findForCheck(providerId).orElseThrow().getAccountId();
        accounts.findByIdWithLock(owner).orElseThrow();
        var payment = payments.findByIdWithLock(providerId).orElseThrow();
        if (!owner.equals(payment.getAccount().getId())) throw new IllegalStateException("Payment ownership changed");
        return payment;
    }

    private PlategaPayment lockByRequest(UUID requestId) {
        var owner = payments.findOwnerByRequestId(requestId).orElseThrow();
        accounts.findByIdWithLock(owner).orElseThrow();
        var payment = payments.findByRequestIdWithLock(requestId).orElseThrow();
        if (!owner.equals(payment.getAccount().getId())) throw new IllegalStateException("Payment ownership changed");
        return payment;
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private TransactionTemplate readOnlyTransaction() {
        var read = transaction();
        read.setReadOnly(true);
        return read;
    }

    private static void setStatus(PlategaPayment payment, PaymentStatus status) {
        payment.setStatus(status);
        payment.setUpdatedAt(Instant.now());
    }

    private static PaymentView paymentView(PlategaPayment payment) {
        return new PaymentView(payment.getId(), payment.getTransactionId(), payment.getAmount().longValueExact(),
                payment.getStatus(), payment.getRedirect());
    }

    private static UUID requestId(String payload) {
        if (payload == null || !payload.startsWith(PAYLOAD_PREFIX)) return null;
        try {
            return UUID.fromString(payload.substring(PAYLOAD_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void validateHttps(String url) {
        var uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Payment URL must be an absolute HTTPS URL");
        }
    }

    private Instant retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                return Instant.now().plusSeconds(Math.max(1, Long.parseLong(value)));
            } catch (NumberFormatException exception) {
                try {
                    return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                } catch (DateTimeParseException ignored) {
                    // Use the default pause for an invalid Retry-After value.
                }
            }
        }
        return Instant.now().plusSeconds(60);
    }
}
