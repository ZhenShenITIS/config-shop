package tg.configshop.messaging.consumers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import tg.configshop.constants.TopUpSource;
import tg.configshop.dto.ConfirmedPayment;
import tg.configshop.events.PaymentConfirmedEvent;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.UserService;

import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
// TODO: Decompose GOD-object
public class ConfirmedPaymentConsumer implements Consumer<ConfirmedPayment> {
    private final TopUpRepository topUpRepository;
    private final UserService userService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void accept(ConfirmedPayment confirmedPayment) {
        log.info("Get confirmed payment userId={}", confirmedPayment.userId());

        Long amount = confirmedPayment.amount();

        userService.addToBalance(confirmedPayment.userId(), amount);
        saveTopUpHistory(confirmedPayment.userId(), amount, confirmedPayment.paymentId());
        applicationEventPublisher.publishEvent(new PaymentConfirmedEvent(this, confirmedPayment.userId(), amount));
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
