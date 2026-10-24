package tg.configshop.telegram.dialogstages.impl;

import lombok.RequiredArgsConstructor;
import tg.configshop.telegram.TelegramUserService;
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
import tg.configshop.constants.WithdrawalType;
import tg.configshop.dto.WithdrawalContext;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.services.ReferralService;
import tg.configshop.telegram.callbacks.impl.withdrawals.WithdrawalCallback; // Предполагаю, что класс называется так, исправь если WithdrawCallback
import tg.configshop.telegram.dialogstages.DialogStage;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoWithdrawSumInputStage implements DialogStage {
    private final TelegramUserService userService;

    private final UserStateRepository userStateRepository;
    private final ReferralService referralService;
    private final WithdrawalCallback withdrawalCallback;

    private final tg.configshop.services.WithdrawalService withdrawalService;

    @Override
    public DialogStageName getDialogStage() {
        return DialogStageName.CRYPTO_WITHDRAW_SUM;
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
        String text = message.getText().trim();

        String responseText;
        boolean success = false;
        long amountToWithdraw = 0;

        try {
            amountToWithdraw = Long.parseLong(text);
            long availableBalance = referralService.getAvailableSumToWithdraw(userService.accountId(userId));

            if (amountToWithdraw < withdrawalService.minimumAmount()) {
                responseText = String.format(MessageText.WITHDRAWAL_SUM_TOO_SMALL.getMessageText(), withdrawalService.minimumAmount());
            } else if (amountToWithdraw > availableBalance) {
                responseText = String.format(MessageText.WITHDRAWAL_INSUFFICIENT_FUNDS.getMessageText(), availableBalance, amountToWithdraw);
            } else {
                success = true;
                responseText = String.format(MessageText.CRYPTO_WALLET_INPUT.getMessageText(), amountToWithdraw);
            }

        } catch (NumberFormatException e) {
            responseText = MessageText.WITHDRAWAL_FORMAT_ERROR.getMessageText();
        } catch (Exception e) {
            log.error("Error in withdrawal sum input", e);
            responseText = MessageText.WITHDRAWAL_ERROR.getMessageText();
        }

        if (success) {
            WithdrawalContext context = new WithdrawalContext();
            context.setAmount(amountToWithdraw);
            context.setWithdrawalType(WithdrawalType.CRYPTO);

            userStateRepository.putWithdrawalContext(userId, context);

            userStateRepository.put(userId, DialogStageName.CRYPTO_WITHDRAW_WALLET);

        }


        sendReply(telegramClient, chatId, responseText);
    }

    private void sendReply(TelegramClient telegramClient, long chatId, String text) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
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
        } catch (Exception e) {
            log.error("Error sending message", e);
        }
    }
}
