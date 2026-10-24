package tg.configshop.telegram.callbacks.impl.devices;

import lombok.RequiredArgsConstructor;
import tg.configshop.telegram.TelegramUserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.exceptions.subscription.SubscriptionNotFoundException;
import tg.configshop.services.ExternalSubscriptionService;
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.callbacks.Callback;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BuyMoreDevicesCallback implements Callback {
    private final TelegramUserService userService;
    private final tg.configshop.services.DevicePurchaseService devicePurchaseService;
    private final SubscriptionService subscriptionService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.BUY_MORE_DEVICES;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();

        try {

            var offer = devicePurchaseService.options(userService.accountId(userId), userService.subscriptionId(userId));
            int availableDevices = offer.availableDevices();
            if (availableDevices <= 0) {
                sendMaxDevicesReachedMessage(callbackQuery, telegramClient, offer.currentDevices());
                return;
            }
            int pricePerDevice = Math.toIntExact(offer.unitPrice());

            sendDeviceSelectionButtons(callbackQuery, telegramClient, pricePerDevice, availableDevices);

        } catch (tg.configshop.telegram.SubscriptionSelectionException e) {
            throw e;
        } catch (IllegalStateException e) {
            sendNoSubscriptionMessage(callbackQuery, telegramClient);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendDeviceSelectionButtons(CallbackQuery callbackQuery, TelegramClient telegramClient,
                                            int pricePerDevice, int availableDevices) {
        String text = MessageText.BUY_MORE_DEVICES_MENU.getMessageText()
                .formatted(pricePerDevice, availableDevices);

        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardRow currentRow = new InlineKeyboardRow();
        int maxToShow = Math.min(availableDevices, 7);

        for (int i = 1; i <= maxToShow; i++) {
            long totalPrice = tg.configshop.services.DevicePurchaseService.priceFor(pricePerDevice, i);
            String buttonText = String.format("+%d (%d ₽)", i, totalPrice);

            currentRow.add(InlineKeyboardButton.builder()
                    .text(buttonText)
                    .callbackData(String.format("%s:%d:%d",
                            CallbackName.PREVIEW_DEVICE_PURCHASE.getCallbackName(),
                            i,
                            pricePerDevice))
                    .build());
            if (i % 3 == 0) {
                rows.add(currentRow);
                currentRow = new InlineKeyboardRow();
            }
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.BACK.getText())
                        .callbackData(CallbackName.DEVICES.getCallbackName())
                        .build()
        ));

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(text)
                .replyMarkup(markup)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendNoSubscriptionMessage(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = MessageText.BUY_MORE_DEVICES_NO_SUBSCRIPTION.getMessageText();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.DEVICES.getCallbackName())
                                .build()
                ))
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(text)
                .replyMarkup(markup)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMaxDevicesReachedMessage(CallbackQuery callbackQuery, TelegramClient telegramClient,
                                              int currentDevices) {
        String text = MessageText.BUY_MORE_DEVICES_MAX_REACHED.getMessageText()
                .formatted(currentDevices);

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.DEVICES.getCallbackName())
                                .build()
                ))
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(text)
                .replyMarkup(markup)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
