package tg.configshop.telegram.callbacks.impl.devices;

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
import tg.configshop.exceptions.devices.TooManyDevicesException;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.model.BotUser;
import tg.configshop.services.DevicePurchaseService;
import tg.configshop.services.SubscriptionService;
import tg.configshop.services.UserService;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class ConfirmDevicePurchaseCallback implements Callback {
    private static final String PAYLOAD_SEPARATOR = ":";

    private final DevicePurchaseService devicePurchaseService;
    private final SubscriptionService subscriptionService;
    private final UserService userService;

    @Value("${SUPPORT_USERNAME}")
    private String supportUsername;

    @Override
    public CallbackName getCallback() {
        return CallbackName.CONFIRM_DEVICE_PURCHASE;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String data = callbackQuery.getData();
        Long userId = callbackQuery.getFrom().getId();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        String[] parts = data.split(PAYLOAD_SEPARATOR);
        if (parts.length != 3) {
            sendUnknownError(telegramClient, chatId, messageId);
            return;
        }

        int deviceCount;
        int pricePerDevice;
        try {
            deviceCount = Integer.parseInt(parts[1]);
            pricePerDevice = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            sendUnknownError(telegramClient, chatId, messageId);
            return;
        }

        long totalPrice = (long) deviceCount * pricePerDevice;

        try {
            devicePurchaseService.purchaseDevices(userId, deviceCount, totalPrice);
            sendSuccessMessage(telegramClient, chatId, messageId, deviceCount, (int) totalPrice);

        } catch (InsufficientBalanceException e) {

            BotUser user = userService.getUser(userId);
            sendInsufficientBalanceMessage(telegramClient, chatId, messageId,
                    deviceCount, (int) totalPrice, user.getBalance().intValue());
        } catch (TooManyDevicesException e) {
            sendTooManyDevicesMessage(telegramClient, chatId, messageId);
        } catch (Exception e) {
            e.printStackTrace();
            sendUnknownError(telegramClient, chatId, messageId);
        }
    }

    private void sendSuccessMessage(TelegramClient client, Long chatId, Integer messageId,
                                    int deviceCount, int totalPrice) {
        String text = MessageText.DEVICE_PURCHASE_SUCCESS.getMessageText()
                .formatted(deviceCount, totalPrice);

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK_TO_MENU.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId, text, keyboard);
    }

    private void sendInsufficientBalanceMessage(TelegramClient client, Long chatId, Integer messageId,
                                                int deviceCount, int totalPrice, int currentBalance) {
        String text = MessageText.DEVICE_PURCHASE_INSUFFICIENT_BALANCE.getMessageText()
                .formatted(deviceCount, totalPrice, currentBalance);

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

        editMessage(client, chatId, messageId, text, keyboard);
    }

    private void sendTooManyDevicesMessage(TelegramClient client, Long chatId, Integer messageId) {
        int maxDeviceCount = subscriptionService.getMaxDeviceCount();

        String text = MessageText.DEVICE_PURCHASE_TOO_MANY.getMessageText()
                .formatted(maxDeviceCount);

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.DEVICES.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(client, chatId, messageId, text, keyboard);
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
