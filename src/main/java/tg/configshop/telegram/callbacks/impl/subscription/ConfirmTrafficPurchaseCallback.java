package tg.configshop.telegram.callbacks.impl.subscription;

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
import tg.configshop.exceptions.traffic.TrafficPackageNotFoundException;
import tg.configshop.services.TrafficPackageService;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class ConfirmTrafficPurchaseCallback implements Callback {
    private static final String PAYLOAD_SEPARATOR = ":";

    private final TrafficPackageService trafficPackageService;

    @Value("${SUPPORT_USERNAME}")
    private String supportUsername;

    @Override
    public CallbackName getCallback() {
        return CallbackName.CONFIRM_TRAFFIC_PURCHASE;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String[] parts = callbackQuery.getData().split(PAYLOAD_SEPARATOR);
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        if (parts.length != 2) {
            sendUnknownError(telegramClient, chatId, messageId);
            return;
        }

        int trafficGb;
        try {
            trafficGb = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendUnknownError(telegramClient, chatId, messageId);
            return;
        }

        try {
            trafficPackageService.buyTraffic(userId, trafficGb);
            sendSuccessMessage(telegramClient, chatId, messageId, trafficGb);
        } catch (InsufficientBalanceException e) {
            sendInsufficientBalanceMessage(telegramClient, chatId, messageId);
        } catch (TrafficPackageNotFoundException e) {
            sendUnknownError(telegramClient, chatId, messageId);
        } catch (Exception e) {
            e.printStackTrace();
            sendUnknownError(telegramClient, chatId, messageId);
        }
    }

    private void sendSuccessMessage(TelegramClient client, Long chatId, Integer messageId, int trafficGb) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK_TO_MENU.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId,
                MessageText.TRAFFIC_PURCHASE_SUCCESS.getMessageText().formatted(trafficGb),
                keyboard);
    }

    private void sendInsufficientBalanceMessage(TelegramClient client, Long chatId, Integer messageId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.TOP_UP.getText())
                                .callbackData(CallbackName.TOP_UP.getCallbackName())
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
