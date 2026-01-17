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
import tg.configshop.constants.MessageText;
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.dto.UserTrafficInGigabytes;
import tg.configshop.model.BotUser;
import tg.configshop.quartz.constants.TrialNotificationType;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.ExternalSubscriptionService;
import tg.configshop.util.DateUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoTrialTrafficNotification implements Job {

    private final BotUserRepository userRepository;
    private final ExternalSubscriptionService externalSubscriptionService;
    private final TelegramClient telegramClient;

    @Value("${SUPPORT_USERNAME}")
    private String supportUsername;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();

        Long userId = dataMap.getLong("userId");

        String typeStr = dataMap.getString("type");
        if (typeStr == null) {
            log.error("Missing required parameter: type");
            throw new JobExecutionException("Missing required parameter: type");
        }

        TrialNotificationType notificationType;
        try {
            notificationType = TrialNotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid notification type: {}", typeStr);
            throw new JobExecutionException("Invalid notification type: " + typeStr);
        }

        log.info("Executing trial traffic check for user {} type {}", userId, notificationType);

        BotUser user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("User {} not found, skipping", userId);
            return;
        }

        if (DateUtil.isExpired(user)) {
            log.info("User {} subscription expired, skipping check", userId);
            return;
        }

        try {
            RemnawaveUser remnawaveUser = externalSubscriptionService.getExternalUser(userId);
            UserTrafficInGigabytes userTraffic = remnawaveUser.userTraffic();

            if (userTraffic.usedTraffic() == 0) {
                log.info("User {} has no traffic, sending notification type {}", userId, notificationType);
                sendNotification(user, notificationType);
            } else {
                log.info("User {} has traffic ({} gigabytes), skipping notification", userId, userTraffic.usedTraffic());
            }

        } catch (Exception e) {
            log.error("Failed to check traffic for user {}: {}", userId, e.getMessage(), e);
            throw new JobExecutionException("Failed to check traffic", e);
        }
    }

    private void sendNotification(BotUser user, TrialNotificationType type) {
        String textToSend;
        switch (type) {
            case HOUR_12 -> textToSend = MessageText.NOTIFICATION_12_HOUR_NO_TRAFFIC.getMessageText();
            case HOUR_24 -> textToSend = MessageText.NOTIFICATION_24_HOUR_NO_TRAFFIC.getMessageText();
            case HOUR_48 -> textToSend = MessageText.NOTIFICATION_48_HOUR_NO_TRAFFIC.getMessageText();
            default -> {
                log.error("Unknown notification type: {}", type);
                throw new RuntimeException("Cannot find correct type of notification: " + type);
            }
        }

        SendMessage sendMessage = SendMessage
                .builder()
                .text(textToSend)
                .parseMode("HTML")
                .chatId(user.getId())
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(InlineKeyboardButton
                                .builder()
                                .text(ButtonText.SUPPORT.getText())
                                .url("https://t.me/" + supportUsername)
                                .build()))
                        .build())
                .build();

        try {
            telegramClient.execute(sendMessage);
            log.info("Trial traffic notification sent to user {}", user.getId());
        } catch (TelegramApiException e) {
            log.error("Failed to send notification to user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to send notification", e);
        }
    }
}
