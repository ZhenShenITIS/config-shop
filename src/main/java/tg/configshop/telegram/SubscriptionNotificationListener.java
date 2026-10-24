package tg.configshop.telegram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;
import tg.configshop.constants.*;
import tg.configshop.events.SubscriptionExpiryDueEvent;
import tg.configshop.events.TrialTrafficUnusedEvent;

@Component @RequiredArgsConstructor @Slf4j
public class SubscriptionNotificationListener {
    private final TelegramUserService users;
    private final TelegramClient client;
    @Value("${SUPPORT_USERNAME}") private String supportUsername;
    @EventListener public void expiry(SubscriptionExpiryDueEvent event) {
        String text = switch (event.type()) {
            case DAYS_LEFT_1 -> MessageText.NOTIFICATION_1_DAY_BEFORE.getMessageText();
            case DAYS_LEFT_3 -> MessageText.NOTIFICATION_3_DAYS_BEFORE.getMessageText();
        };
        send(event.accountId(), text, InlineKeyboardButton.builder().text(ButtonText.BUY_SUB.getText())
                .callbackData(CallbackName.BUY_SUB_MENU.getCallbackName()).build());
    }
    @EventListener public void trial(TrialTrafficUnusedEvent event) {
        String text = switch (event.type()) {
            case HOUR_12 -> MessageText.NOTIFICATION_12_HOUR_NO_TRAFFIC.getMessageText();
            case HOUR_24 -> MessageText.NOTIFICATION_24_HOUR_NO_TRAFFIC.getMessageText();
            case HOUR_48 -> MessageText.NOTIFICATION_48_HOUR_NO_TRAFFIC.getMessageText();
        };
        send(event.accountId(), text, InlineKeyboardButton.builder().text(ButtonText.SUPPORT.getText())
                .url("https://t.me/" + supportUsername).build());
    }
    private void send(java.util.UUID accountId, String text, InlineKeyboardButton button) {
        users.findByAccountId(accountId).ifPresent(user -> {
            try {
                client.execute(SendMessage.builder().chatId(user.getId()).text(text).parseMode("HTML")
                        .replyMarkup(InlineKeyboardMarkup.builder().keyboardRow(new InlineKeyboardRow(button)).build()).build());
            } catch (Exception e) { log.error("Notification delivery failed for account {}", accountId, e); }
        });
    }
}
