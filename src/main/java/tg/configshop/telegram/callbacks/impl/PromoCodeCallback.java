package tg.configshop.telegram.callbacks.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class PromoCodeCallback implements Callback {
    private final CallbackName callbackName = CallbackName.PROMO_CODE;

    @Override
    public CallbackName getCallback() {
        return callbackName;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        long userId = callbackQuery.getFrom().getId();
        // TODO Put user in dialog stage
        String text = MessageText.PROMO_CODE_REQUEST.getMessageText();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                .build()
                ))
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(markup)
                .parseMode("HTML")
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
