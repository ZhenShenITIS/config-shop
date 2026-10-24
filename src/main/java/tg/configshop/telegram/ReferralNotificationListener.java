package tg.configshop.telegram;

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
import tg.configshop.util.StringUtil;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReferralNotificationListener {

    private final TelegramClient telegramClient;
    private final TelegramUserService users;

    @Async
    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void handleRewardNotification(ReferralRewardEvent event) {

        var recipient = users.findByAccountId(event.referrerAccountId());
        if (recipient.isEmpty()) return;
        var source = users.findByAccountId(event.sourceAccountId());
        String text = source.map(profile -> MessageText.REFERRAL_REWARD_NOTIFICATION.getMessageText().formatted(
                event.level(), profile.getId(), getSafeName(profile.getFirstName()), getSafeUsername(profile.getUsername()),
                event.purchaseAmount(), event.percentage(), event.rewardAmount()))
                .orElse("💸 <b>Реферальное начисление!</b>\n\nРеферал " + event.level() + "-го уровня пополнил баланс на "
                        + event.purchaseAmount() + " ₽. Вам зачислено: " + event.rewardAmount() + " ₽.");

        SendMessage sendMessage = SendMessage.builder()
                .chatId(recipient.get().getId())
                .text(text)
                .parseMode("HTML")
                .build();

        try {
            telegramClient.execute(sendMessage);
            log.info("Referral reward notification sent to user {}", recipient.get().getId());
        } catch (TelegramApiException e) {
            log.error("Failed to send referral reward notification to user {}: {}", recipient.get().getId(), e.getMessage());
        }
    }

    private String getSafeName(String firstName) {
        return firstName == null || firstName.isBlank()
                ? "Пользователь"
                : StringUtil.getSafeHtmlString(firstName);
    }

    private String getSafeUsername(String username) {
        return username == null || username.isBlank()
                ? "скрыт"
                : StringUtil.getSafeHtmlString(username);
    }
}
