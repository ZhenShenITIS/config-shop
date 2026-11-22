package tg.configshop.alerting;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import tg.configshop.constants.MessageText;

import java.util.UUID;

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

    public void alertPasswordResetDeliveryFailure(UUID webUserId) {
        telegramSenderWithRateLimit.sendMessageInTopic(chatId, topicId,
                "Не удалось отправить письмо для сброса пароля. ID веб-профиля: " + webUserId);
    }
}
