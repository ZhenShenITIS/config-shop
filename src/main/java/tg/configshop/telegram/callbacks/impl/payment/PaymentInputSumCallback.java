package tg.configshop.telegram.callbacks.impl.payment;

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
import tg.configshop.constants.DialogStageName;
import tg.configshop.constants.MessageText;
import tg.configshop.dto.PaymentContext;
import tg.configshop.external_api.pay.constants.PaymentMethod;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class PaymentInputSumCallback implements Callback {
    private final UserStateRepository userStateRepository;
    @Override
    public CallbackName getCallback() {
        return CallbackName.PAYMENT_INPUT_SUM;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        long userId = callbackQuery.getFrom().getId();
        int paymentMethod = Integer.parseInt(callbackQuery.getData().split(":")[1]);

        userStateRepository.putPaymentContext(userId, new PaymentContext(PaymentMethod.fromIntMethod(paymentMethod)));

        userStateRepository.put(userId, DialogStageName.PAYMENT);
        EditMessageText editMessageText = EditMessageText
                .builder()
                .text(MessageText.INPUT_SUM_PAYMENT.getMessageText())
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(ButtonText.BACK.getText())
                                        .callbackData(CallbackName.TOP_UP.getCallbackName())
                                        .build()
                        ))
                        .build())
                .build();
        try {
            telegramClient.execute(editMessageText);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
