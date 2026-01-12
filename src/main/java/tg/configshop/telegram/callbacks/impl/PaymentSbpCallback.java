package tg.configshop.telegram.callbacks.impl;

import lombok.RequiredArgsConstructor;
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
import tg.configshop.constants.DialogStageName;
import tg.configshop.constants.MessageText;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class PaymentSbpCallback implements Callback {
    private final UserStateRepository userStateRepository;
    @Override
    public CallbackName getCallback() {
        return CallbackName.PAYMENT_SBP;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        long userId = callbackQuery.getFrom().getId();

        userStateRepository.put(userId, DialogStageName.SBP_PAY);
        EditMessageText editMessageText = EditMessageText
                .builder()
                .text(MessageText.INPUT_SUM_PAYMENT.getMessageText())
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(ButtonText.BACK.getText())
                                        .callbackData(CallbackName.TOP_UP.getCallbackName())
                                        .build()
                        ))
                        .build())
                .build();
        try {
            telegramClient.execute(editMessageText);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
