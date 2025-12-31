package tg.configshop.telegram.dialogstages.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.constants.DialogStageName;
import tg.configshop.telegram.dialogstages.DialogStage;

@Component
public class NoneDialogStage implements DialogStage {
    DialogStageName dialogStageName = DialogStageName.NONE;
    @Override
    public DialogStageName getDialogStage() {
        return dialogStageName;
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {

    }

    @Override
    public void answerMessage(Message message, TelegramClient telegramClient) {

    }
}
