package tg.configshop.telegram.callbacks.impl.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
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
import tg.configshop.constants.PaymentResult;
import tg.configshop.dto.PaymentContext;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.services.PaymentService;
import tg.configshop.telegram.callbacks.Callback;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckStatusPaymentCallback implements Callback {
    public static final String PAYLOAD_SEPARATOR = ":";
    private final PaymentService paymentService;
    private final UserStateRepository stateRepository;

    private final long DELAY_BETWEEN_CHECKS = 30_000;

    @Override
    public CallbackName getCallback() {
        return CallbackName.CHECK_STATUS_PAYMENT;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long userId = callbackQuery.getFrom().getId();
        String callbackId = callbackQuery.getId();
        long now = System.currentTimeMillis();
        PaymentContext paymentContext = stateRepository.getPaymentContext(userId);
        if (!(now - DELAY_BETWEEN_CHECKS > paymentContext.lastCheckTime())) {
            answerQuery(callbackId, MessageText.PROCESSING_PAY.getMessageText(), telegramClient);
            log.info("Requests for payment status updates are too frequent. UserId: {}", userId);
            return;
        }
        stateRepository.putPaymentContext(userId, new PaymentContext(paymentContext.paymentMethod(), now));
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();

        String data = callbackQuery.getData();

        String[] parts = data.split(PAYLOAD_SEPARATOR);

        if (parts.length < 2) {
            return;
        }

        String transactionId = parts[1];

        PaymentResult paymentResult = paymentService.checkPayment(transactionId, userId);
        log.info("Send request to payment service. Result: {}, userId: {}", paymentResult, userId);

        switch (paymentResult) {
            case CONFIRMED ->
                    editMessage(chatId, messageId, MessageText.CONFIRMED_PAY.getMessageText(), telegramClient);
            case CANCELED ->
                    editMessage(chatId, messageId, MessageText.CANCELED_PAY.getMessageText(), telegramClient);
            case PROCESSING ->
                answerQuery(callbackId, MessageText.PROCESSING_PAY.getMessageText(), telegramClient);
            case EXPIRED ->
               answerQuery(callbackId, MessageText.EXPIRED_PAY.getMessageText(), telegramClient);

        }


    }

    private void editMessage(long chatId, int messageId, String text, TelegramClient telegramClient) {
        EditMessageText editMessageText = EditMessageText
                .builder()
                .text(text)
                .chatId(chatId)
                .parseMode("HTML")
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton
                                        .builder()
                                        .text(ButtonText.BACK_TO_MENU.getText())
                                        .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                        .build()

                        ))
                        .build())
                .messageId(messageId)
                .build();
        try {
            telegramClient.execute(editMessageText);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void answerQuery (String callbackId, String text, TelegramClient telegramClient) {
        AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery
                .builder()
                .text(text)
                .callbackQueryId(callbackId)
                .showAlert(true)
                .build();
        try {
            telegramClient.execute(answerCallbackQuery);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
