package tg.configshop.telegram.callbacks.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class PurchaseCallback implements Callback {

    private final SubscriptionService subscriptionService;
    private static final String PAYLOAD_SEPARATOR = ":";

    @Value("${SUPPORT_USERNAME}")
    private String supportUsername;

    @Override
    public CallbackName getCallback() {
        return CallbackName.PURCHASE;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        String[] parts = data.split(PAYLOAD_SEPARATOR);
        if (parts.length < 2) {
            sendUnknownError(telegramClient, chatId, messageId);
            return;
        }

        Long subscriptionId;
        try {
            subscriptionId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            sendUnknownError(telegramClient, chatId, messageId);
            return;
        }

        try {
            subscriptionService.buySubscription(userId, subscriptionId);
            sendSuccessMessage(telegramClient, chatId, messageId);
        } catch (InsufficientBalanceException e) {
            sendInsufficientBalanceMessage(telegramClient, chatId, messageId);
        } catch (Exception e) {
            sendUnknownError(telegramClient, chatId, messageId);
        }
    }

    private void sendSuccessMessage(TelegramClient client, Long chatId, Integer messageId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK_TO_MENU.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId, MessageText.PURCHASE_SUCCESS.getMessageText(), keyboard);
    }

    private void sendInsufficientBalanceMessage(TelegramClient client, Long chatId, Integer messageId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.TOP_UP.getText())
                                .callbackData(CallbackName.TOP_UP.getCallbackName()) // Заглушка, как ты просил
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK_TO_MENU.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId, MessageText.INSUFFICIENT_BALANCE.getMessageText(), keyboard);
    }

    private void sendUnknownError(TelegramClient client, Long chatId, Integer messageId) {

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.SUPPORT.getText())
                                .url("https://t.me/" + supportUsername)
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK_TO_MENU.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId, MessageText.UNKNOWN_ERROR.getMessageText(), keyboard);
    }

    private void editMessage(TelegramClient client, Long chatId, Integer messageId, String text, InlineKeyboardMarkup markup) {
        try {
            client.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
