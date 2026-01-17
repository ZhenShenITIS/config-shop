package tg.configshop.quartz.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.model.BotUser;
import tg.configshop.quartz.constants.ExpirySubNotificationType;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.util.DateUtil;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryNotificationJob implements Job {
    private final BotUserRepository userRepository;

    private final TelegramClient telegramClient;




    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        Long userId = dataMap.getLongValue("userId");
        ExpirySubNotificationType notificationType = ExpirySubNotificationType.valueOf(dataMap.getString("type"));
        Instant targetExpireAt = Instant.ofEpochMilli(dataMap.getLong("expireAt"));

        log.info("Executing notification job for user {} type {}", userId, notificationType);

        BotUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("User {} not found, skipping", userId);
            return;
        }

        if (!user.getExpireAt().equals(targetExpireAt)) {
            log.info("User {} expireAt changed, skipping outdated notification", userId);
            return;
        }

        if (DateUtil.isExpired(user)) {
            log.info("User {} subscription already expired", userId);
            return;
        }
        sendMessage(user, notificationType);
        log.info("Notification sent to user {}", userId);
    }

    private void sendMessage (BotUser botUser, ExpirySubNotificationType type) {
        String textToSend;
        switch (type) {
            case DAYS_LEFT_1 -> textToSend = MessageText.NOTIFICATION_1_DAY_BEFORE.getMessageText();
            case DAYS_LEFT_3 -> textToSend = MessageText.NOTIFICATION_3_DAYS_BEFORE.getMessageText();
            default -> throw new RuntimeException("Cannot find correct type of notification");
        }

        SendMessage sendMessage = SendMessage
                .builder()
                .text(textToSend)
                .parseMode("HTML")
                .chatId(botUser.getId())
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(InlineKeyboardButton
                                .builder()
                                .text(ButtonText.BUY_SUB.getText())
                                .callbackData(CallbackName.BUY_SUB_MENU.getCallbackName())
                                .build()))
                        .build())
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

}
