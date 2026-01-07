package tg.configshop.telegram.callbacks.impl;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.telegram.callbacks.Callback;

public class DocsDeclineCallback implements Callback {
    @Override
    public CallbackName getCallback() {
        return CallbackName.DOCS_DECLINE;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(MessageText.DOCS_DECLINE.getMessageText())
                .showAlert(true)
                .build();

        try {
            telegramClient.execute(answer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
