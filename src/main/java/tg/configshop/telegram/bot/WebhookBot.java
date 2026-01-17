package tg.configshop.telegram.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.handlers.CallbackQueryHandler;
import tg.configshop.telegram.handlers.MessageHandler;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "START_WEBHOOK", havingValue = "true")
public class WebhookBot {

    private final CallbackQueryHandler callbackQueryHandler;
    private final MessageHandler messageHandler;
    private final TelegramClient telegramClient;

    public void processUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            callbackQueryHandler.processCallbackQuery(
                    update.getCallbackQuery(),
                    telegramClient
            );
        } else if (update.hasMessage()) {
            messageHandler.answerMessage(
                    update.getMessage(),
                    telegramClient
            );
        }
    }
}
