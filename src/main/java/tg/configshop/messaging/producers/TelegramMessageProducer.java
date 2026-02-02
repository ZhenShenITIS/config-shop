package tg.configshop.messaging.producers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramMessageProducer {

    private final StreamBridge streamBridge;

    public void sendTelegramUpdateToKafka(Message message) {
        long userId = message.getFrom().getId();

        org.springframework.messaging.Message<Message> springMessage = MessageBuilder
                .withPayload(message)
                .setHeader(KafkaHeaders.KEY, userId)
                .build();
        streamBridge.send("telegramMessage-out-0", springMessage);
        log.info("Message sent to Kafka: userId={}", userId);
    }
}
