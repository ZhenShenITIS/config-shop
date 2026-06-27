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

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BuyTrafficMenuCallback implements Callback {
    private static final String PAYLOAD_SEPARATOR = ":";

    private final TrafficPackageService trafficPackageService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.BUY_TRAFFIC_MENU;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow currentRow = new InlineKeyboardRow();

        for (TrafficPackage trafficPackage : trafficPackageService.getAvailableTrafficPackages()) {
            currentRow.add(InlineKeyboardButton.builder()
                    .text(ButtonText.TRAFFIC_PACKAGE.getText().formatted(
                            trafficPackage.getTrafficGb(),
                            trafficPackage.getCost()
                    ))
                    .callbackData(CallbackName.PREVIEW_TRAFFIC_PURCHASE.getCallbackName()
                                  + PAYLOAD_SEPARATOR
                                  + trafficPackage.getTrafficGb())
                    .build());

            if (currentRow.size() == 2) {
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
                        .callbackData(CallbackName.BUY_MENU.getCallbackName())
                        .build()
        ));

        editMessage(telegramClient,
                callbackQuery.getMessage().getChatId(),
                callbackQuery.getMessage().getMessageId(),
                MessageText.BUY_TRAFFIC_MENU.getMessageText(),
                new InlineKeyboardMarkup(rows));
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
