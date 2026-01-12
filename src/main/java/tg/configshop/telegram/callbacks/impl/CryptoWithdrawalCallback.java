package tg.configshop.telegram.callbacks.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.services.ReferralService;
import tg.configshop.telegram.callbacks.Callback;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CryptoWithdrawalCallback implements Callback {
    private final ReferralService referralService;
    private final UserStateRepository userStateRepository;

    @Override
    public CallbackName getCallback() {
        return CallbackName.CRYPTO_WITHDRAWAL;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();
        long userId = callbackQuery.getFrom().getId();

        long availableBalance = referralService.getAvailableSumToWithdraw(userId);

        List<InlineKeyboardRow> keyboard = new ArrayList<>();

        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(ButtonText.BACK.getText())
                .callbackData(CallbackName.WITHDRAW.getCallbackName())
                .build());
        keyboard.add(row);

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(keyboard)
                .build();

        EditMessageText message = EditMessageText.builder()
                .chatId(chatId)
                .text(String.format(MessageText.CRYPTO_WITHDRAWAL_INPUT.getMessageText(), availableBalance))
                .replyMarkup(markup)
                .parseMode("HTML")
                .messageId(messageId)
                .build();


        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        userStateRepository.put(userId, DialogStageName.CRYPTO_WITHDRAW_SUM);
    }
}
