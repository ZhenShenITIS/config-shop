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
import tg.configshop.exceptions.CyclicReferralException;
import tg.configshop.exceptions.PromoCodeAlreadyUsedException;
import tg.configshop.exceptions.PromoCodeEndedException;
import tg.configshop.exceptions.PromoCodeNotFoundException;
import tg.configshop.exceptions.ReferralPromoCodeAlreadyUsedException;
import tg.configshop.exceptions.SelfReferralException;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.services.PromoCodeService;
import tg.configshop.telegram.callbacks.impl.BackToMenuCallback;
import tg.configshop.telegram.dialogstages.DialogStage;

@RequiredArgsConstructor
@Component
public class PromoCodeInputDialogStage implements DialogStage {
    private final PromoCodeService promoCodeService;
    private final UserStateRepository stateRepository;
    private final BackToMenuCallback backToMenuCallback;

    @Override
    public DialogStageName getDialogStage() {
        return DialogStageName.PROMO_CODE_INPUT;
    }

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        if (CallbackName.BACK_TO_MENU.getCallbackName().equals(callbackQuery.getData())) {
            stateRepository.put(callbackQuery.getFrom().getId(), DialogStageName.NONE);
            backToMenuCallback.processCallback(callbackQuery, telegramClient);
            return;
        }
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .text(MessageText.PROMO_CODE_ALERT.getMessageText())
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

            promoCodeService.activatePromoCode(code, userId);
            responseText = MessageText.PROMO_SUCCESS.getMessageText();
            stateRepository.put(userId, DialogStageName.NONE);

        } catch (PromoCodeNotFoundException e) {
            responseText = MessageText.PROMO_NOT_FOUND.getMessageText();
        } catch (PromoCodeAlreadyUsedException e) {
            responseText = MessageText.PROMO_ALREADY_USED.getMessageText();
        } catch (PromoCodeEndedException e) {
            responseText = MessageText.PROMO_ENDED.getMessageText();
        } catch (ReferralPromoCodeAlreadyUsedException e) {
            responseText = MessageText.REFERRAL_PROMO_ALREADY_USED.getMessageText();
        } catch (CyclicReferralException e) {
            responseText = MessageText.CYCLIC_REFERRAL_EXCEPTION.getMessageText();
        } catch (SelfReferralException e) {
            responseText = MessageText.SELF_REFERRAL_EXCEPTION.getMessageText();
        } catch (Exception e) {
            responseText = MessageText.PROMO_ERROR.getMessageText();
            e.printStackTrace();
        }

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
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
