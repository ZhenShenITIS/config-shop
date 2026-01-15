package tg.configshop.telegram.callbacks.impl.devices;

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
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class PreviewDevicePurchaseCallback implements Callback {
    private static final String PAYLOAD_SEPARATOR = ":";

    @Override
    public CallbackName getCallback() {
        return CallbackName.PREVIEW_DEVICE_PURCHASE;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        String[] parts = data.split(PAYLOAD_SEPARATOR);
        if (parts.length != 3) {
            sendErrorMessage(telegramClient, chatId, messageId);
            return;
        }

        int deviceCount;
        int pricePerDevice;
        try {
            deviceCount = Integer.parseInt(parts[1]);
            pricePerDevice = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            sendErrorMessage(telegramClient, chatId, messageId);
            return;
        }

        int totalPrice = deviceCount * pricePerDevice;

        showConfirmationDialog(telegramClient, chatId, messageId, deviceCount, pricePerDevice, totalPrice);
    }

    private void showConfirmationDialog(TelegramClient client, Long chatId, Integer messageId,
                                        int deviceCount, int pricePerDevice, int totalPrice) {
        String text = MessageText.DEVICE_PURCHASE_CONFIRMATION.getMessageText()
                .formatted(deviceCount, pricePerDevice, totalPrice);

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.CONFIRM_BUY.getText())
                                .callbackData(String.format("%s:%d:%d",
                                        CallbackName.CONFIRM_DEVICE_PURCHASE.getCallbackName(),
                                        deviceCount,
                                        pricePerDevice))
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.BUY_MORE_DEVICES.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId, text, keyboard);
    }

    private void sendErrorMessage(TelegramClient client, Long chatId, Integer messageId) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.DEVICES.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId,
                MessageText.UNKNOWN_ERROR.getMessageText(), keyboard);
    }

    private void editMessage(TelegramClient client, Long chatId, Integer messageId,
                             String text, InlineKeyboardMarkup markup) {
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
