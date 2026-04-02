package tg.configshop.telegram.bot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.alerting.AlertService;
import tg.configshop.telegram.config.TelegramConfig;
import tg.configshop.telegram.handlers.CallbackQueryHandler;
import tg.configshop.telegram.handlers.MessageHandler;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "start.webhook", havingValue = "false", matchIfMissing = true)
public class ShopBot extends AbstractBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;

    private final TelegramConfig telegramConfig;

    private final CallbackQueryHandler callbackQueryHandler;

    private final MessageHandler messageHandler;

    private final AlertService alertService;



    @Override
    public void consume(Update update) {
        Thread.startVirtualThread(() -> {
            try {
                consumeUpdate(update);
            } catch (Exception e) {
                extractLog(update, e);
                alertService.alertUnknownException(update, e);
            }
        });
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