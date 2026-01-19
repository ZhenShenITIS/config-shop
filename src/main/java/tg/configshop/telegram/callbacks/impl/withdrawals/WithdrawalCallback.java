package tg.configshop.telegram.callbacks.impl.withdrawals;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.telegram.callbacks.Callback;

import java.util.ArrayList;
import java.util.List;

@Component
public class WithdrawalCallback implements Callback {
    @Override
    public CallbackName getCallback() {
        return CallbackName.WITHDRAW;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        InlineKeyboardRow row1 = new InlineKeyboardRow();
        row1.add(InlineKeyboardButton.builder()
                .text(ButtonText.PAYMENT_METHOD_CRYPTO.getText())
                .callbackData(CallbackName.CRYPTO_WITHDRAWAL.getCallbackName())
                .build());
        keyboard.add(row1);

        InlineKeyboardRow row2 = new InlineKeyboardRow();
        row2.add(InlineKeyboardButton.builder()
                .text(ButtonText.BACK.getText())
                .callbackData(CallbackName.REFERRAL.getCallbackName())
                .build());
        keyboard.add(row2);

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();

        EditMessageText message = EditMessageText.builder()
                .chatId(chatId)
                .text(MessageText.WITHDRAWAL_METHOD.getMessageText())
                .replyMarkup(markup)
                .messageId(messageId)
                .parseMode("HTML")
                .build();

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
