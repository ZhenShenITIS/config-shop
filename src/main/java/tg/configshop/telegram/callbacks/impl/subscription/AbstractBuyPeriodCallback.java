package tg.configshop.telegram.callbacks.impl.subscription;

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
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.callbacks.Callback;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractBuyPeriodCallback implements Callback {

    protected final SubscriptionService subscriptionService;

    private static final String PAYLOAD_SEPARATOR = ":";

    public AbstractBuyPeriodCallback(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    protected abstract int getDaysPeriod();

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String data = callbackQuery.getData();
        int selectedDevices = parseDevicesFromCallback(data);

        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        EditMessageText editMessageText = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(MessageText.BUY_SUBSCRIPTION_MENU_DEVICE.getMessageText())
                .replyMarkup(createDeviceSelectionKeyboard(selectedDevices))
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(editMessageText);

        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private int parseDevicesFromCallback(String data) {
        if (data.contains(PAYLOAD_SEPARATOR)) {
            try {
                return Integer.parseInt(data.split(PAYLOAD_SEPARATOR)[1]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return subscriptionService.getMinDeviceCount();
    }

    private InlineKeyboardMarkup createDeviceSelectionKeyboard(int selectedDevices) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRowButtons = new ArrayList<>();

        int min = subscriptionService.getMinDeviceCount();
        int max = subscriptionService.getMaxDeviceCount();
        int days = getDaysPeriod();
        int basePrice = subscriptionService.getBaseSubscriptionCostByDays(days);
        int extraPrice = subscriptionService.getExtraPricePerDeviceByDays(days);

        for (int devices = min; devices <= max; devices++) {
            boolean isSelected = (devices == selectedDevices);

            int extraDevsCount = Math.max(0, devices - min);
            long totalPrice = basePrice + (long) extraDevsCount * extraPrice;

            String text;
            if (isSelected) {
                text = ButtonText.DEVICE_OPTION_SELECTED.getText().formatted(devices, totalPrice);
            } else {
                text = ButtonText.DEVICE_OPTION_UNSELECTED.getText().formatted(devices, totalPrice);
            }

            String callbackPayload = getCallback().getCallbackName() + PAYLOAD_SEPARATOR + devices;

            currentRowButtons.add(InlineKeyboardButton.builder()
                    .text(text)
                    .callbackData(callbackPayload)
                    .build());

            if (currentRowButtons.size() == 2) {
                rows.add(new InlineKeyboardRow(currentRowButtons));
                currentRowButtons = new ArrayList<>();
            }
        }

        if (!currentRowButtons.isEmpty()) {
            rows.add(new InlineKeyboardRow(currentRowButtons));
        }


        String confirmPayload = CallbackName.CONFIRM_BUY.getCallbackName() + PAYLOAD_SEPARATOR + days + PAYLOAD_SEPARATOR + selectedDevices;

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.CONFIRM_BUY.getText())
                        .callbackData(confirmPayload)
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.BACK.getText())
                        .callbackData(CallbackName.BUY_SUB_MENU.getCallbackName())
                        .build()
        ));

        return new InlineKeyboardMarkup(rows);
    }
}
