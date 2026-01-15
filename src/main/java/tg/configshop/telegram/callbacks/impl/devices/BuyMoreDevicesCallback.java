package tg.configshop.telegram.callbacks.impl.devices;

import lombok.RequiredArgsConstructor;
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
    private final ExternalSubscriptionService externalSubscriptionService;
    private final SubscriptionService subscriptionService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.BUY_MORE_DEVICES;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();

        try {

            RemnawaveUser user = externalSubscriptionService.getExternalUser(userId);


            if (!user.isActive() || user.daysLeft() <= 0) {
                sendNoSubscriptionMessage(callbackQuery, telegramClient);
                return;
            }


            int maxDevices = subscriptionService.getMaxDeviceCount();
            int currentDevices = user.hwidDeviceLimit();
            int availableDevices = maxDevices - currentDevices;

            if (availableDevices <= 0) {
                sendMaxDevicesReachedMessage(callbackQuery, telegramClient, currentDevices);
                return;
            }


            int pricePerDevice = calculatePricePerDevice(user.daysLeft());

            sendDeviceSelectionButtons(callbackQuery, telegramClient, pricePerDevice, availableDevices);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int calculatePricePerDevice(long daysLeft) {
        try {
            if (daysLeft < 30) {
                return subscriptionService.getExtraPricePerDeviceByDays(30);
            } else if (daysLeft < 90) {
                return subscriptionService.getExtraPricePerDeviceByDays(90);
            } else if (daysLeft < 180) {
                return subscriptionService.getExtraPricePerDeviceByDays(180);
            } else if (daysLeft < 360) {
                return subscriptionService.getExtraPricePerDeviceByDays(360);
            } else {
                return 2 * subscriptionService.getExtraPricePerDeviceByDays(360);
            }
        } catch (SubscriptionNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to calculate device price", e);
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
            int totalPrice = i * pricePerDevice;
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
