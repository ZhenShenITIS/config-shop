package tg.configshop.alerting;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Duration;

@Slf4j
@Component
public class TelegramSenderWithRateLimit {

    private final TelegramClient telegramClient;
    private final Bucket bucket;

    private static final int MAX_MESSAGE_LENGTH = 1000;

    public TelegramSenderWithRateLimit(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;


        Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public void sendMessageInTopic(Long chatId, Integer topicId, String message) {
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit превышен! Сообщение не отправлено в чат: {}, топик: {}. Текст: {}",
                    chatId, topicId, truncateMessage(message, 100));
            return;
        }


        String safeMessage = truncateMessage(message, MAX_MESSAGE_LENGTH);


        SendMessage sendMessage = SendMessage
                .builder()
                .text(safeMessage)
                .parseMode("HTML")
                .chatId(chatId)
                .messageThreadId(topicId)
                .build();


        try {
            telegramClient.executeAsync(sendMessage);
        } catch (TelegramApiException e) {

            log.error("Ошибка при отправке сообщения в Telegram", e);
        }
    }


    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return "null";
        }
        if (message.length() <= maxLength) {
            return message;
        }

        return message.substring(0, maxLength - 13) + "\n...[TRUNCATED]";
    }
}