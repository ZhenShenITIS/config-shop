package tg.configshop.telegram.bot;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.config.TelegramConfig;
import tg.configshop.telegram.handlers.CallbackQueryHandler;
import tg.configshop.telegram.handlers.MessageHandler;


@Component
public class ShopBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;

    private final TelegramConfig telegramConfig;

    private CallbackQueryHandler callbackQueryHandler;

    private MessageHandler messageHandler;


    public ShopBot(TelegramConfig telegramConfig, CallbackQueryHandler callbackQueryHandler, MessageHandler messageHandler) {
        this.telegramConfig = telegramConfig;
        this.telegramClient = new OkHttpTelegramClient(getBotToken());
        this.callbackQueryHandler = callbackQueryHandler;
        this.messageHandler = messageHandler;
    }



    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            callbackQueryHandler.processCallbackQuery(update.getCallbackQuery(), telegramClient);
        } else if (update.hasMessage()) {
            messageHandler.answerMessage(update.getMessage(), telegramClient);

        }
    }

    @Override
    public String getBotToken() {
        return telegramConfig.getBotToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }
}