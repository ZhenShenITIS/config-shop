package tg.configshop.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.MessageText;
import tg.configshop.events.ReferralRewardEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralNotificationListener {

    private final TelegramClient telegramClient;

    @Async
    @EventListener
    public void handleRewardNotification(ReferralRewardEvent event) {

        String text = MessageText.REFERRAL_REWARD_NOTIFICATION.getMessageText().formatted(
                event.getLevel(),
                event.getPurchaseAmount(),
                event.getPercentage(),
                event.getRewardAmount()
        );

        SendMessage sendMessage = SendMessage.builder()
                .chatId(event.getReferrerId())
                .text(text)
                .parseMode("HTML")
                .build();

        try {
            telegramClient.execute(sendMessage);
            log.info("Referral reward notification sent to user {}", event.getReferrerId());
        } catch (TelegramApiException e) {
            log.error("Failed to send referral reward notification to user {}: {}", event.getReferrerId(), e.getMessage());
        }
    }
}
