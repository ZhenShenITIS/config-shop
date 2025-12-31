package tg.configshop.telegram.callbacks.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.telegram.constants.CallbackName;

@Component
public class NoneCallback implements Callback {
    private CallbackName callbackName = CallbackName.NONE;
    @Override
    public CallbackName getCallback() {
        return callbackName;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {

    }
}
