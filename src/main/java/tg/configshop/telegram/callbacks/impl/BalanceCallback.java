package tg.configshop.telegram.callbacks.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.model.BotUser;
import tg.configshop.services.UserService;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class BalanceCallback implements Callback {
    private final CallbackName callbackName = CallbackName.BALANCE;
    private final UserService userService;

    @Override
    public CallbackName getCallback() {
        return callbackName;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        long userId = callbackQuery.getFrom().getId();

        BotUser botUser = userService.getUser(userId);
        String balance = botUser.getBalance().toString();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(MessageText.BALANCE_MENU.getMessageText().formatted(balance))
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text(ButtonText.HISTORY.getText())
                                        .callbackData(CallbackName.HISTORY.getCallbackName())
                                        .build(),
                                InlineKeyboardButton.builder()
                                        .text(ButtonText.TOP_UP.getText())
                                        .callbackData(CallbackName.TOP_UP.getCallbackName())
                                        .build()
                        ))
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text(ButtonText.BACK.getText())
                                        .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                        .build()
                        ))
                        .build())
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
