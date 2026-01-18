package tg.configshop.telegram.dialogstages.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.DialogStageName;
import tg.configshop.constants.MessageText;
import tg.configshop.external_api.pay.model.PlategaPayment;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.services.PaymentService;
import tg.configshop.telegram.callbacks.impl.payment.TopUpCallback;
import tg.configshop.telegram.dialogstages.DialogStage;
import tg.configshop.telegram.dto.BotMessageParams;
import tg.configshop.telegram.message_body.StartCommandBody;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentStage implements DialogStage {
    private final UserStateRepository stateRepository;
    private final TopUpCallback topUpCallback;
    private final PaymentService paymentService;
    private final StartCommandBody startCommandBody;

    public static final String PAYLOAD_SEPARATOR = ":";
    public static final long MIN_PAY_AMOUNT = 100;
    public static final long MAX_PAY_AMOUNT = 10000;


    @Override
    public DialogStageName getDialogStage() {
        return DialogStageName.PAYMENT;
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        if (CallbackName.TOP_UP.getCallbackName().equals(callbackQuery.getData())) {
            stateRepository.put(callbackQuery.getFrom().getId(), DialogStageName.NONE);
            topUpCallback.processCallback(callbackQuery, telegramClient);
            return;
        }
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(MessageText.INPUT_SUM_PAYMENT.getMessageText())
                .showAlert(false)
                .build();

        try {
            telegramClient.execute(answer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void answerMessage(Message message, TelegramClient telegramClient) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        long amount;
        try {
            amount = Long.parseLong(message.getText());
        } catch (NumberFormatException e) {
            sendNumberFormatError(chatId, telegramClient);
            return;
        }
        if (amount < MIN_PAY_AMOUNT || amount > MAX_PAY_AMOUNT) {
            sendSumError(chatId, telegramClient);
            return;
        }
        stateRepository.put(userId, DialogStageName.NONE);
        PlategaPayment plategaPayment = paymentService.createPlategaPayment(amount, stateRepository.getPaymentContext(userId).paymentMethod().getIntMethod(), userId);

        String shortId = plategaPayment.getTransactionId().split("-")[0];

        String text = String.format(MessageText.PAYMENT_INSTRUCTION.getMessageText(), amount, shortId);

        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardButton payButton = InlineKeyboardButton.builder()
                .text(ButtonText.PAY_ACTION.getText())
                .url(plategaPayment.getRedirect())
                .build();
        rows.add(new InlineKeyboardRow(payButton));

        String checkPayload = CallbackName.CHECK_STATUS_PAYMENT.getCallbackName()
                              + PAYLOAD_SEPARATOR
                              + plategaPayment.getTransactionId();

        InlineKeyboardButton checkButton = InlineKeyboardButton.builder()
                .text(ButtonText.CHECK_PAYMENT.getText())
                .callbackData(checkPayload)
                .build();
        rows.add(new InlineKeyboardRow(checkButton));

        SendMessage paymentMessage = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();

        try {
            telegramClient.execute(paymentMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

    }

    private void sendNumberFormatError (long chatId, TelegramClient telegramClient) {
        SendMessage sendMessage = SendMessage
                .builder()
                .chatId(chatId)
                .text(MessageText.NUMBER_FORMAT_ERROR.getMessageText())
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendSumError (long chatId, TelegramClient telegramClient) {
        SendMessage sendMessage = SendMessage
                .builder()
                .chatId(chatId)
                .text(MessageText.SUM_PAY_ERROR.getMessageText().formatted(MIN_PAY_AMOUNT, MAX_PAY_AMOUNT))
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
