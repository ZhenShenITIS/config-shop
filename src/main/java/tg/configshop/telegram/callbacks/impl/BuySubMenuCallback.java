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
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class BuySubMenuCallback implements Callback {

    private final SubscriptionService subscriptionService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.BUY_SUB_MENU;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.SUB_PERIOD_1_MONTH.getText().formatted(
                                        subscriptionService.getBaseSubscriptionCostByDays(30)
                                ))
                                .callbackData(CallbackName.BUY_PERIOD_30.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.SUB_PERIOD_3_MONTH.getText().formatted(
                                        subscriptionService.getBaseSubscriptionCostByDays(90)
                                ))
                                .callbackData(CallbackName.BUY_PERIOD_90.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.SUB_PERIOD_6_MONTH.getText().formatted(
                                        subscriptionService.getBaseSubscriptionCostByDays(180)
                                ))
                                .callbackData(CallbackName.BUY_PERIOD_180.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.SUB_PERIOD_12_MONTH.getText().formatted(
                                        subscriptionService.getBaseSubscriptionCostByDays(360)
                                ))
                                .callbackData(CallbackName.BUY_PERIOD_360.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.SUBSCRIPTION.getCallbackName()) // Ведем назад в меню подписки
                                .build()
                ))
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(MessageText.BUY_SUBSCRIPTION_MENU_PERIOD.getMessageText())
                .parseMode("HTML")
                .replyMarkup(keyboard)
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
