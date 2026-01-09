package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.MessageText;
import tg.configshop.constants.PaymentResult;
import tg.configshop.constants.TopUpSource;
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
import tg.configshop.services.UserService;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PlategaClient plategaClient;
    private final PlategaPaymentRepository paymentRepository;
    private final UserService userService;
    private final TopUpRepository topUpRepository;

    @Value("${TELEGRAM_BOT_USERNAME}")
    private String telegramBotUsername;

    @Override
    public PlategaPayment createPlategaPayment(Long amount, int paymentMethodInt, Long userId) {
        CreatePaymentRequest createPaymentRequest = new CreatePaymentRequest(
                paymentMethodInt,
                new PaymentDetails(amount.doubleValue(), "RUB"),
                MessageText.PAYMENT_DESCRIPTION.getMessageText().formatted(userId),
                "https://t.me/" + telegramBotUsername,
                "https://t.me/" + telegramBotUsername,
                "...");
        CreatePaymentResponse createPaymentResponse = plategaClient.createPayment(createPaymentRequest);
        PlategaPayment plategaPayment = PlategaPayment
                .builder()
                .paymentDetails(createPaymentResponse.paymentDetails())
                .amount(Double.parseDouble(createPaymentResponse.paymentDetails().split(" ")[0]))
                .currency(createPaymentResponse.paymentDetails().split(" ")[1])
                .status(createPaymentResponse.status())
                .paymentMethod(createPaymentResponse.paymentMethod())
                .cryptoAmount(createPaymentResponse.cryptoAmount())
                .redirect(createPaymentResponse.redirect())
                .returnUrl(createPaymentResponse.returnUrl())
                .transactionId(createPaymentResponse.transactionId())
                .usdtRate(createPaymentResponse.usdtRate())
                .build();
        paymentRepository.save(plategaPayment);
        return plategaPayment;
    }

    @Override
    @Transactional
    public PaymentResult checkPayment(String paymentId, Long userId) {
        PlategaPayment payment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));


        if (!PaymentStatus.PENDING.equals(payment.getStatus())) {
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
            paymentRepository.save(payment);

            if (remoteStatus == PaymentStatus.CONFIRMED) {
                Long amount = payment.getAmount().longValue();

                userService.addToBalance(userId, amount);
                saveTopUpHistory(userId, amount, paymentId);

                return PaymentResult.CONFIRMED;
            } else if (remoteStatus == PaymentStatus.CANCELED) {
                return PaymentResult.CANCELED;
            }
        }
        return PaymentResult.PROCESSING;
    }

    private void saveTopUpHistory(Long userId, Long amount, String externalId) {
        TopUp topUp = TopUp.builder()
                .botUser(userService.getUser(userId))
                .value(amount)
                .topUpSource(TopUpSource.EXTERNAL)
                .externalId(externalId)
                .build();
        topUpRepository.save(topUp);
    }
}
