package tg.configshop.telegram.callbacks.impl.other;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.DialogStageName;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.TelegramUserService;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.telegram.dialogstages.impl.WebsiteCodeDialogStage;

@Component
@RequiredArgsConstructor
public class ConnectWebsiteCallback implements Callback {
    private final TelegramUserService users;
    private final UserStateRepository states;

    @Override
    public CallbackName getCallback() {
        return CallbackName.CONNECT_WEBSITE;
    }

    @Override
    public void processCallback(CallbackQuery query, TelegramClient client) {
        long telegramId = query.getFrom().getId();
        String error = null;
        if (query.getMessage() == null || query.getMessage().getChatId() != telegramId) {
            error = "Введите код в личном чате с ботом.";
        } else if (users.getUser(telegramId) == null) {
            error = "Сначала зарегистрируйтесь в боте через /start.";
        }

        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId())
                    .text(error).showAlert(error != null).build());
            if (error != null) return;
            states.put(telegramId, DialogStageName.WEBSITE_CODE);
            client.execute(SendMessage.builder().chatId(telegramId)
                    .text("Введите шестизначный код со страницы «Привязать Telegram».")
                    .replyMarkup(WebsiteCodeDialogStage.backButton()).build());
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Cannot request website code", e);
        }
    }
}
