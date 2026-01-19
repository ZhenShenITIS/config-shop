package tg.configshop.telegram.dialogstages.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.DialogStageName;
import tg.configshop.constants.MessageText;
import tg.configshop.dto.WithdrawalContext;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.callbacks.impl.withdrawals.WithdrawalCallback;
import tg.configshop.telegram.dialogstages.DialogStage;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoWithdrawWalletInputStage implements DialogStage {

    private final UserStateRepository userStateRepository;
    private final WithdrawalCallback withdrawalCallback;

    @Override
    public DialogStageName getDialogStage() {
        return DialogStageName.CRYPTO_WITHDRAW_WALLET;
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String data = callbackQuery.getData();
        long userId = callbackQuery.getFrom().getId();

        if (CallbackName.WITHDRAW.getCallbackName().equals(data)) {
            userStateRepository.put(userId, DialogStageName.NONE);
            withdrawalCallback.processCallback(callbackQuery, telegramClient);
            return;
        }

        try {
            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .build());
        } catch (Exception e) {
            log.error("Error answering callback", e);
        }
    }

    @Override
    public void answerMessage(Message message, TelegramClient telegramClient) {
        long userId = message.getFrom().getId();
        long chatId = message.getChatId();
        String walletAddress = message.getText().trim();

        WithdrawalContext context = userStateRepository.getWithdrawalContext(userId);

        if (context == null) {
            userStateRepository.put(userId, DialogStageName.NONE);
            sendError(telegramClient, chatId);
            return;
        }

        context.setRequisites(walletAddress);
        userStateRepository.putWithdrawalContext(userId, context);

        String text = String.format(MessageText.WITHDRAWAL_CONFIRMATION.getMessageText(),
                context.getAmount(),
                walletAddress);

        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.CONFIRM_BUY.getText()) // Используем "Подтвердить"
                        .callbackData(CallbackName.CONFIRM_WITHDRAWAL.getCallbackName())
                        .build()
        ));


        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.BACK.getText())
                        .callbackData(CallbackName.WITHDRAW.getCallbackName())
                        .build()
        ));

        SendMessage sm = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();

        try {
            telegramClient.execute(sm);
            userStateRepository.put(userId, DialogStageName.NONE);
        } catch (Exception e) {
            log.error("Error sending confirmation", e);
        }
    }

    private void sendError(TelegramClient client, long chatId) {
        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(MessageText.UNKNOWN_ERROR.getMessageText())
                    .parseMode("HTML")
                    .build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
