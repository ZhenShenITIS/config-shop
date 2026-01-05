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
import tg.configshop.model.Subscription;
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.callbacks.Callback;


@Component
@RequiredArgsConstructor
public class ConfirmBuyCallback implements Callback {

    private final SubscriptionService subscriptionService;
    private static final String PAYLOAD_SEPARATOR = ":";

    @Override
    public CallbackName getCallback() {
        return CallbackName.CONFIRM_BUY;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String data = callbackQuery.getData();

        String[] parts = data.split(PAYLOAD_SEPARATOR);

        if (parts.length < 3) {
            return;
        }

        int days;
        int devices;

        try {
            days = Integer.parseInt(parts[1]);
            devices = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }


        Subscription subscription = subscriptionService.getSubscriptionByDaysAndDevices(days, devices);


        int minDevices = subscriptionService.getMinDeviceCount();
        int basePrice = subscriptionService.getBaseSubscriptionCostByDays(days);

        int extraDevicesCount = Math.max(0, devices - minDevices);
        int extraPricePerUnit = subscriptionService.getExtraPricePerDeviceByDays(days);
        int totalExtraPrice = extraDevicesCount * extraPricePerUnit;

        long finalPrice = subscription.getCost();

        String periodText = formatPeriod(days);

        String summaryText = MessageText.CONFIRM_BUY_SUMMARY.getMessageText().formatted(
                periodText,
                devices,
                basePrice,
                extraDevicesCount,
                totalExtraPrice,
                finalPrice
        );

        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        String backCallbackData = getBackCallbackNameByDays(days) + PAYLOAD_SEPARATOR + devices;

        String payCallbackData = CallbackName.PURCHASE.getCallbackName() + PAYLOAD_SEPARATOR + subscription.getId();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.CONFIRM_PAYMENT.getText().formatted(finalPrice))
                                .callbackData(payCallbackData)
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(backCallbackData)
                                .build()
                ))
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(summaryText)
                .parseMode("HTML")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
        }
    }

    private String getBackCallbackNameByDays(int days) {
        return switch (days) {
            case 30 -> CallbackName.BUY_PERIOD_30.getCallbackName();
            case 90 -> CallbackName.BUY_PERIOD_90.getCallbackName();
            case 180 -> CallbackName.BUY_PERIOD_180.getCallbackName();
            case 360 -> CallbackName.BUY_PERIOD_360.getCallbackName();
            default -> CallbackName.BUY_SUB_MENU.getCallbackName();
        };
    }

    private String formatPeriod(int days) {
        return switch (days) {
            case 30 -> "1 месяц";
            case 90 -> "3 месяца";
            case 180 -> "6 месяцев";
            case 360 -> "1 год";
            default -> days + " дней";
        };
    }
}
