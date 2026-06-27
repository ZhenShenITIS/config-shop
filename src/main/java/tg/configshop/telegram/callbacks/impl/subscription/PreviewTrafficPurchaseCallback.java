package tg.configshop.telegram.callbacks.impl.subscription;

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
import tg.configshop.model.TrafficPackage;
import tg.configshop.services.TrafficPackageService;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class PreviewTrafficPurchaseCallback implements Callback {
    private static final String PAYLOAD_SEPARATOR = ":";

    private final TrafficPackageService trafficPackageService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.PREVIEW_TRAFFIC_PURCHASE;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String[] parts = callbackQuery.getData().split(PAYLOAD_SEPARATOR);
        if (parts.length != 2) {
            sendUnknownError(callbackQuery, telegramClient);
            return;
        }

        int trafficGb;
        try {
            trafficGb = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendUnknownError(callbackQuery, telegramClient);
            return;
        }

        try {
            TrafficPackage trafficPackage = trafficPackageService.getTrafficPackageByGb(trafficGb);
            showConfirmationDialog(callbackQuery, telegramClient, trafficPackage);
        } catch (Exception e) {
            sendUnknownError(callbackQuery, telegramClient);
        }
    }

    private void showConfirmationDialog(CallbackQuery callbackQuery, TelegramClient telegramClient, TrafficPackage trafficPackage) {
        String text = MessageText.TRAFFIC_PURCHASE_CONFIRMATION.getMessageText()
                .formatted(trafficPackage.getTrafficGb(), trafficPackage.getCost());

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.CONFIRM_BUY.getText())
                                .callbackData(CallbackName.CONFIRM_TRAFFIC_PURCHASE.getCallbackName()
                                              + PAYLOAD_SEPARATOR
                                              + trafficPackage.getTrafficGb())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.BUY_TRAFFIC_MENU.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(telegramClient,
                callbackQuery.getMessage().getChatId(),
                callbackQuery.getMessage().getMessageId(),
                text,
                keyboard);
    }

    private void sendUnknownError(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.BUY_TRAFFIC_MENU.getCallbackName())
                                .build()
                ))
                .build();

        editMessage(telegramClient,
                callbackQuery.getMessage().getChatId(),
                callbackQuery.getMessage().getMessageId(),
                MessageText.UNKNOWN_ERROR.getMessageText(),
                keyboard);
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
