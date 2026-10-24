package tg.configshop.services.impl;

import tg.configshop.services.BalanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.PaymentResult;
import tg.configshop.constants.TopUpSource;
import tg.configshop.events.PaymentConfirmedEvent;
import tg.configshop.external_api.pay.PlategaClient;
import tg.configshop.external_api.pay.constants.PaymentStatus;
import tg.configshop.external_api.pay.dto.CreatePaymentRequest;
import tg.configshop.external_api.pay.dto.CreatePaymentResponse;
import tg.configshop.external_api.pay.dto.PaymentDetails;
import tg.configshop.external_api.pay.model.PlategaPayment;
import tg.configshop.external_api.pay.repositories.PlategaPaymentRepository;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.PaymentService;
import tg.configshop.services.AccountService;
import tg.configshop.dto.PaymentView;
import tg.configshop.dto.PaymentLimits;

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
    @Transactional
    public PaymentResult checkPayment(String paymentId, java.util.UUID accountId) {
        PlategaPayment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElse(null);
        if (payment == null) return PaymentResult.MANUAL_REVIEW;
        if (!payment.getAccount().getId().equals(accountId)) {
            throw new IllegalArgumentException("Payment belongs to another account");
        }


        if (topUpRepository.existsByPlatega_Id(payment.getId())
                || !PaymentStatus.PENDING.equals(payment.getStatus())) {
            return PaymentResult.EXPIRED;
        }

        PaymentStatus remoteStatus;
        try {
            // TODO remove HTTP-request from @Transactional
            remoteStatus = plategaClient.updateStatus(paymentId);
        } catch (Exception e) {
            log.error("Error fetching status from Platega", e);
            return PaymentResult.PROCESSING;
        }

        if (payment.getStatus() != remoteStatus) {
            payment.setStatus(remoteStatus);
            payment.setUpdatedAt(java.time.Instant.now());
            paymentRepository.save(payment);

            if (remoteStatus == PaymentStatus.CONFIRMED) {
                if (!"RUB".equals(payment.getCurrency())) throw new IllegalStateException("Unsupported currency");
                Long amount = payment.getAmount().longValueExact();

                saveTopUpHistory(amount, payment);
                applicationEventPublisher.publishEvent(new PaymentConfirmedEvent(accountId, amount, payment.getId()));

                return PaymentResult.CONFIRMED;
            } else if (remoteStatus == PaymentStatus.CANCELED) {
                return PaymentResult.CANCELED;
            }
        }
        return PaymentResult.PROCESSING;
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
