package tg.configshop.telegram.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.messaging.producers.TelegramCallbackQueryProducer;
import tg.configshop.messaging.producers.TelegramMessageProducer;
import tg.configshop.telegram.handlers.CallbackQueryHandler;
import tg.configshop.telegram.handlers.MessageHandler;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "start.webhook", havingValue = "true")
public class WebhookBot {

    private final CallbackQueryHandler callbackQueryHandler;
    private final MessageHandler messageHandler;
    private final TelegramClient telegramClient;
    private final TelegramMessageProducer telegramMessageProducer;
    private final TelegramCallbackQueryProducer telegramCallbackQueryProducer;

    public void processUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            telegramCallbackQueryProducer.sendTelegramUpdateToKafka(update.getCallbackQuery());
            callbackQueryHandler.processCallbackQuery(
                    update.getCallbackQuery(),
                    telegramClient
            );
        } else if (update.hasMessage()) {
            telegramMessageProducer.sendTelegramUpdateToKafka(update.getMessage());
            messageHandler.answerMessage(
                    update.getMessage(),
                    telegramClient
            );
        }
    }
}
