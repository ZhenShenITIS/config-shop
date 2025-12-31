package tg.configshop.telegram.handlers;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;



public interface CallbackQueryHandler {
    void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient);
}
