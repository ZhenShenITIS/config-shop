package tg.configshop.telegram.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.config.TelegramConfig;
import tg.configshop.telegram.handlers.CallbackQueryHandler;
import tg.configshop.telegram.handlers.MessageHandler;


@Component
@RequiredArgsConstructor
public class ShopBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;

    private final TelegramConfig telegramConfig;

    private final CallbackQueryHandler callbackQueryHandler;

    private final MessageHandler messageHandler;



    @Override
    public void consume(Update update) {
        Thread.startVirtualThread(() -> consumeUpdate(update));
    }

    @Override
    public String getBotToken() {
        return telegramConfig.getBotToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    private void consumeUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            callbackQueryHandler.processCallbackQuery(update.getCallbackQuery(), telegramClient);
        } else if (update.hasMessage()) {
            messageHandler.answerMessage(update.getMessage(), telegramClient);

        }
    }
}