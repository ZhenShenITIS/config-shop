package tg.configshop.telegram.dialogstages;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.DialogStageName;


public interface DialogStage {
    DialogStageName getDialogStage();
    void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient);
    void answerMessage(Message message, TelegramClient telegramClient);
}
