package tg.configshop.alerting;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.MessageText;

@Component
@RequiredArgsConstructor
public class AlertService {
    private final TelegramSenderWithRateLimit telegramSenderWithRateLimit;

    @Value("${ALERT_CHAT_ID}")
    private Long chatId;

    @Value("${ALERT_THREAD_ID}")
    private Integer topicId;

    public void alertUnknownException(Update update, Exception e) {
        User user = null;
        if (update.hasMessage()) {
            user = update.getMessage().getFrom();
        } else if (update.hasCallbackQuery()) {
            user = update.getCallbackQuery().getFrom();
        }
        String text = MessageText.ALERT_MESSAGE.getMessageText().formatted(
                user != null ? user.getId() : null,
                user != null ? user.getUserName() : null,
                e.toString()
        );
        telegramSenderWithRateLimit.sendMessageInTopic(chatId, topicId, text);
    }
}
