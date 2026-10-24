package tg.configshop.services.impl;

import tg.configshop.services.BalanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import tg.configshop.constants.PaymentResult;
import tg.configshop.constants.TopUpSource;
import tg.configshop.events.PaymentConfirmedEvent;
import tg.configshop.external_api.pay.PlategaClient;
import tg.configshop.external_api.pay.constants.PaymentStatus;
import tg.configshop.external_api.pay.dto.CreatePaymentRequest;
import tg.configshop.external_api.pay.dto.CreatePaymentResponse;
import tg.configshop.external_api.pay.dto.PaymentDetails;
import tg.configshop.external_api.pay.dto.PaymentStatusResponse;
import tg.configshop.external_api.pay.model.PlategaPayment;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.PaymentService;
import tg.configshop.services.AccountService;
import tg.configshop.dto.PaymentView;
import tg.configshop.dto.PaymentLimits;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PlategaClient plategaClient;
    private final PlategaPaymentRepository paymentRepository;
    private final AccountService accounts;
    private final BalanceService balanceService;
    private final TopUpRepository topUpRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final PlatformTransactionManager transactionManager;
    private volatile Instant retryStatusAfter = Instant.MIN;

    @Override public PaymentLimits limits() { return new PaymentLimits(100, 10000); }

    @Override
    public PaymentView createPlategaPayment(Long amount, int paymentMethodInt, java.util.UUID accountId, String returnUrl, String failedUrl) {
        if (amount == null || amount < limits().minimum() || amount > limits().maximum())
            throw new IllegalArgumentException("Payment amount is outside allowed range");
        tg.configshop.external_api.pay.constants.PaymentMethod.fromIntMethod(paymentMethodInt);
        var account = accounts.requireAccount(accountId);
        for (String url : new String[]{returnUrl, failedUrl}) {
            var uri = java.net.URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Payment return URL must be an absolute HTTPS URL");
        }
        CreatePaymentRequest createPaymentRequest = new CreatePaymentRequest(
                paymentMethodInt,
                new PaymentDetails(amount.doubleValue(), "RUB"),
                "Пополнение баланса аккаунта " + accountId,
                returnUrl,
                failedUrl,
                "...");
        CreatePaymentResponse createPaymentResponse = plategaClient.createPayment(createPaymentRequest);
        String[] details = createPaymentResponse.paymentDetails().trim().split("\\s+");
        java.math.BigDecimal providerAmount = new java.math.BigDecimal(details[0]);
        if (!"RUB".equals(details[1]) || providerAmount.compareTo(java.math.BigDecimal.valueOf(amount)) != 0) {
            throw new IllegalStateException("Provider payment amount/currency differs from requested payment");
        }
        PlategaPayment plategaPayment = PlategaPayment
                .builder()
                .amount(providerAmount)
                .currency(details[1])
                .status(createPaymentResponse.status())
                .paymentMethod(createPaymentResponse.paymentMethod())
                .redirect(createPaymentResponse.redirect())
                .transactionId(createPaymentResponse.transactionId())
                .account(account)
                .build();
        paymentRepository.save(plategaPayment);
        return new PaymentView(plategaPayment.getId(), plategaPayment.getTransactionId(), amount, plategaPayment.getStatus(), plategaPayment.getRedirect());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResult checkPayment(String paymentId, UUID accountId) {
        var transaction = new TransactionTemplate(transactionManager);
        PaymentResult current = transaction.execute(tx -> paymentRepository.findForCheck(paymentId)
                .map(payment -> currentResult(payment.getId(), payment.getAccountId(), payment.getStatus(), accountId))
                .orElse(PaymentResult.MANUAL_REVIEW));
        if (current != PaymentResult.PROCESSING) return current;
        if (Instant.now().isBefore(retryStatusAfter)) return PaymentResult.PROCESSING;

        PaymentStatusResponse response;
        try {
            response = plategaClient.getPayment(paymentId);
        } catch (HttpClientErrorException.TooManyRequests e) {
            retryStatusAfter = retryAfter(e.getResponseHeaders());
            log.warn("Platega status checks paused until {}", retryStatusAfter);
            return PaymentResult.PROCESSING;
        } catch (Exception e) {
            log.error("Error fetching payment {} from Platega", paymentId, e);
            return PaymentResult.PROCESSING;
        }

        return transaction.execute(tx -> {
            var payment = paymentRepository.findByIdWithLock(paymentId).orElse(null);
            if (payment == null) return PaymentResult.MANUAL_REVIEW;
            var result = currentResult(payment.getId(), payment.getAccount().getId(), payment.getStatus(), accountId);
            if (result != PaymentResult.PROCESSING) return result;

            if (response == null || response.status() == null || !paymentId.equals(response.id())
                    || response.paymentDetails() == null || response.paymentDetails().amount() == null
                    || payment.getAmount().compareTo(response.paymentDetails().amount()) != 0
                    || !payment.getCurrency().equals(response.paymentDetails().currency())
                    || !"RUB".equals(payment.getCurrency())) {
                log.error("Invalid Platega response for payment {}", paymentId);
                return PaymentResult.MANUAL_REVIEW;
            }
            if (response.status() == PaymentStatus.PENDING) return PaymentResult.PROCESSING;

            payment.setStatus(response.status());
            payment.setUpdatedAt(Instant.now());
            paymentRepository.save(payment);
            if (response.status() == PaymentStatus.CONFIRMED) {
                Long amount = payment.getAmount().longValueExact();
                saveTopUpHistory(amount, payment);
                applicationEventPublisher.publishEvent(new PaymentConfirmedEvent(accountId, amount, payment.getId()));
                return PaymentResult.CONFIRMED;
            }
            return response.status() == PaymentStatus.CANCELED ? PaymentResult.CANCELED : PaymentResult.MANUAL_REVIEW;
        });
    }

    private PaymentResult currentResult(UUID paymentId, UUID ownerId, PaymentStatus status, UUID accountId) {
        if (!ownerId.equals(accountId)) throw new IllegalArgumentException("Payment belongs to another account");
        if (status == PaymentStatus.CHARGEBACKED) return PaymentResult.MANUAL_REVIEW;
        if (topUpRepository.existsByPlatega_Id(paymentId)) {
            return status == PaymentStatus.PENDING || status == PaymentStatus.CONFIRMED
                    ? PaymentResult.CONFIRMED : PaymentResult.MANUAL_REVIEW;
        }
        return switch (status) {
            case PENDING -> PaymentResult.PROCESSING;
            case CANCELED -> PaymentResult.CANCELED;
            default -> PaymentResult.MANUAL_REVIEW;
        };
    }

    private Instant retryAfter(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value != null) {
            try {
                return Instant.now().plusSeconds(Math.max(1, Long.parseLong(value)));
            } catch (NumberFormatException e) {
                try {
                    return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                } catch (DateTimeParseException ignored) {
                    // Use a short pause if the provider sent an invalid Retry-After value.
                }
            }
        }
        return Instant.now().plusSeconds(60);
    }

    private void saveTopUpHistory(Long amount, PlategaPayment payment) {
        TopUp topUp = TopUp.builder()
                .account(payment.getAccount())
                .value(amount)
                .topUpSource(TopUpSource.EXTERNAL)
                .platega(payment)
                .build();
        balanceService.credit(topUp);
    }
}
