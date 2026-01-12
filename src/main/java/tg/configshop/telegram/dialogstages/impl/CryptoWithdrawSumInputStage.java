package tg.configshop.telegram.dialogstages.impl;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.DialogStageName;
import tg.configshop.telegram.dialogstages.DialogStage;

public class CryptoWithdrawSumInputStage implements DialogStage {
    @Override
    public DialogStageName getDialogStage() {
        return DialogStageName.CRYPTO_WITHDRAW_SUM;
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {

    }

    @Override
    public void answerMessage(Message message, TelegramClient telegramClient) {

    }
}
