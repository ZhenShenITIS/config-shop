package tg.configshop.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.events.PaymentConfirmedEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {
    private final TelegramUserService users;
    private final TelegramClient telegramClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentConfirmedEvent event) {
        users.findByAccountId(event.accountId()).ifPresent(user -> {
            var message = SendMessage.builder()
                    .chatId(user.getId())
                    .text(MessageText.PAYMENT_CONFIRMED_NOTIFICATION.getMessageText().formatted(event.amount()))
                    .parseMode("HTML")
                    .replyMarkup(InlineKeyboardMarkup.builder().keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text(ButtonText.BACK_TO_MENU.getText())
                                    .callbackData(CallbackName.BACK_TO_MENU.getCallbackName()).build())).build())
                    .build();
            try {
                telegramClient.execute(message);
                log.info("Payment notification sent for payment {} to user {}", event.paymentId(), user.getId());
            } catch (TelegramApiException e) {
                log.error("Failed to send payment notification for payment {} to user {}", event.paymentId(), user.getId(), e);
            }
        });
    }
}
