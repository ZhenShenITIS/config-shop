package tg.configshop.messaging.producers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramCallbackQueryProducer {

    private final StreamBridge streamBridge;

    public void sendTelegramUpdateToKafka(CallbackQuery callbackQuery) {
        long userId = callbackQuery.getFrom().getId();
        Message<CallbackQuery> message = MessageBuilder
                .withPayload(callbackQuery)
                .setHeader(KafkaHeaders.KEY, userId)
                .build();

        streamBridge.send("telegramCallback-out-0", message);

        log.info("CallbackQuery sent to Kafka via StreamBridge: userId={}", userId);
    }
}
