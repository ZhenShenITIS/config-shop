package tg.configshop.telegram.dialogstages.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.DialogStageName;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.callbacks.impl.other.BackToMenuCallback;
import tg.configshop.telegram.dialogstages.DialogStage;
import tg.configshop.web.registration.TelegramWebLinkService;
import tg.configshop.web.registration.WebAuthException;

import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebsiteCodeDialogStage implements DialogStage {
    private final UserStateRepository states;
    private final TelegramWebLinkService links;
    private final BackToMenuCallback menu;

    @Override
    public DialogStageName getDialogStage() {
        return DialogStageName.WEBSITE_CODE;
    }

    @Override
    public void processCallbackQuery(CallbackQuery query, TelegramClient client) {
        Long telegramId = query.getFrom().getId();
        if (query.getMessage() == null || !Objects.equals(query.getMessage().getChatId(), telegramId)) return;
        if (CallbackName.BACK_TO_MENU.getCallbackName().equals(query.getData())) {
            states.put(telegramId, DialogStageName.NONE);
            try {
                client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId()).build());
            } catch (TelegramApiException e) {
                log.warn("Cannot acknowledge website code cancellation for Telegram user {}", telegramId, e);
            }
            menu.processCallback(query, client);
            return;
        }
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId())
                    .text("Введите шестизначный код или вернитесь в меню.").build());
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Cannot answer website code callback", e);
        }
    }

    @Override
    public void answerMessage(Message message, TelegramClient client) {
        long telegramId = message.getFrom().getId();
        if (message.getChatId() != telegramId) return;

        String text;
        try {
            links.redeem(telegramId, message.hasText() ? message.getText().trim() : "");
            states.put(telegramId, DialogStageName.NONE);
            text = "Сайт и Telegram привязаны к одному аккаунту.";
        } catch (WebAuthException e) {
            text = e.getMessage();
        } catch (DataAccessException e) {
            log.warn("Website account merge failed for Telegram user {}", telegramId, e);
            text = "Не удалось объединить аккаунты. Попробуйте ещё раз.";
        }
        try {
            client.execute(SendMessage.builder().chatId(telegramId).text(text).replyMarkup(backButton()).build());
        } catch (TelegramApiException e) {
            throw new IllegalStateException("Cannot deliver website link result", e);
        }
    }

    public static InlineKeyboardMarkup backButton() {
        return InlineKeyboardMarkup.builder().keyboardRow(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text(ButtonText.BACK_TO_MENU.getText()).callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                .build())).build();
    }
}
