package tg.configshop.telegram.callbacks.impl.other;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.services.RegistrationService;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.telegram.dto.BotMessageParams;
import tg.configshop.telegram.message_body.StartCommandBody;

@Component
@RequiredArgsConstructor
public class DocsConfirmCallback implements Callback {
    private final RegistrationService registrationService;
    private final StartCommandBody startCommandBody;
    @Override
    public CallbackName getCallback() {
        return CallbackName.DOCS_CONFIRM;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long referrerId = null;
        if (callbackQuery.getData().contains(":")) {
            referrerId = Long.parseLong(callbackQuery.getData().split(":")[1]);
        }
        registrationService.registerUser(callbackQuery.getFrom(), referrerId);
        BotMessageParams botMessageParams = startCommandBody.getMessage(callbackQuery.getFrom(), referrerId);
        EditMessageText editMessageText = EditMessageText
                .builder()
                .text(botMessageParams.text())
                .messageId(callbackQuery.getMessage().getMessageId())
                .chatId(callbackQuery.getMessage().getChatId())
                .replyMarkup(botMessageParams.inlineKeyboard())
                .parseMode("HTML")
                .build();

        try {
            telegramClient.execute(editMessageText);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
