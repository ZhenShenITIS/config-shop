package tg.configshop.telegram.callbacks.impl.referrals;

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
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class AddRefPromoCallback implements Callback {
    private final UserStateRepository userStateRepository;
    @Override
    public CallbackName getCallback() {
        return CallbackName.ADD_REF_PROMO;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        userStateRepository.put(callbackQuery.getFrom().getId(), DialogStageName.ADD_REF_PROMO_INPUT);
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();


        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(MessageText.INPUT_REF_PROMO.getMessageText())
                .replyMarkup(InlineKeyboardMarkup
                        .builder()
                        .keyboardRow(new InlineKeyboardRow(
                                InlineKeyboardButton.builder()
                                        .text(ButtonText.BACK.getText())
                                        .callbackData(CallbackName.REFERRAL.getCallbackName())
                                        .build()))
                        .build())
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
