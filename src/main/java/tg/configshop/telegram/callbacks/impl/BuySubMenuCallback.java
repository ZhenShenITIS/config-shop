package tg.configshop.telegram.callbacks.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.telegram.callbacks.Callback;

@Component
public class BuySubMenuCallback implements Callback {
    @Override
    public CallbackName getCallback() {
        return CallbackName.BUY_SUB_MENU;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
    }
}
