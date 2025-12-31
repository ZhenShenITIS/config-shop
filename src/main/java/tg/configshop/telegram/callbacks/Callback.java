package tg.configshop.telegram.callbacks;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.constants.CallbackName;


public interface Callback {
    CallbackName getCallback();
    void processCallback (CallbackQuery callbackQuery, TelegramClient telegramClient);

}
