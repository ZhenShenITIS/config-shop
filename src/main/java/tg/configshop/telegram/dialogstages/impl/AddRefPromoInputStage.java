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
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.DialogStageName;
import tg.configshop.constants.MessageText;
import tg.configshop.exceptions.promocode.InvalidSymbolsPromoException;
import tg.configshop.exceptions.promocode.PromoCodeAlreadyExistException;
import tg.configshop.exceptions.promocode.TooManyReferralPromoException;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.services.PromoCodeService;
import tg.configshop.telegram.callbacks.impl.referrals.ReferralCallback;
import tg.configshop.telegram.dialogstages.DialogStage;

@Component
@RequiredArgsConstructor
public class AddRefPromoInputStage implements DialogStage {
    private final UserStateRepository stateRepository;
    private final ReferralCallback referralCallback;
    private final PromoCodeService promoCodeService;

    @Override
    public DialogStageName getDialogStage() {
        return DialogStageName.ADD_REF_PROMO_INPUT;
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        if (CallbackName.REFERRAL.getCallbackName().equals(callbackQuery.getData())) {
            stateRepository.put(callbackQuery.getFrom().getId(), DialogStageName.NONE);
            referralCallback.processCallback(callbackQuery, telegramClient);
            return;
        }
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(MessageText.INPUT_REF_PROMO_NOTIFY.getMessageText())
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
        long userId = message.getFrom().getId();
        String code = message.getText().trim();
        String responseText;

        try {

            promoCodeService.createReferralPromoCode(code, userId);
            responseText = MessageText.ADD_REF_PROMO_SUCCESS.getMessageText();
            stateRepository.put(userId, DialogStageName.NONE);

        } catch (TooManyReferralPromoException e) {
            responseText = MessageText.TOO_MANY_REFERRAL_PROMO.getMessageText();
        } catch (PromoCodeAlreadyExistException e) {
            responseText = MessageText.PROMO_CODE_ALREADY_EXIST.getMessageText();
        } catch (InvalidSymbolsPromoException e) {
            responseText = MessageText.INVALID_SYMBOLS_USE.getMessageText();
        } catch (Exception e) {
            responseText = MessageText.UNKNOWN_ERROR_REF_PROMO.getMessageText();
            e.printStackTrace();
        }

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.REFERRAL.getCallbackName())
                                .build()
                ))
                .build();

        SendMessage sendMessage = SendMessage.builder()
                .chatId(message.getChatId())
                .text(responseText)
                .replyMarkup(markup)
                .parseMode("HTML")
                .build();

        try {
            telegramClient.execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
