package tg.configshop.telegram.callbacks.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.telegram.callbacks.Callback;

@Component
public class RefAnalyticsCallback implements Callback {
    @Override
    public CallbackName getCallback() {
        return CallbackName.REF_ANALYTICS;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {

    }
}
