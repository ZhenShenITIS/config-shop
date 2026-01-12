package tg.configshop.telegram.callbacks;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;

public class WithdrawalCallback implements Callback {
    @Override
    public CallbackName getCallback() {
        return CallbackName.WITHDRAW;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        // TODO
    }
}
