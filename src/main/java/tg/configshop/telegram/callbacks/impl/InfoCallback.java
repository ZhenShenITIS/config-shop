package tg.configshop.telegram.callbacks.impl;

import org.springframework.beans.factory.annotation.Value;
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
public class InfoCallback implements Callback {

    @Value("${AGREEMENT_URL}")
    private String agreementUrl;

    @Value("${POLICY_URL}")
    private String policyUrl;

    @Override
    public CallbackName getCallback() {
        return CallbackName.DOCS_INFO;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();

        String text = MessageText.LEGAL_TEXT.getMessageText().formatted(agreementUrl, policyUrl);

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
