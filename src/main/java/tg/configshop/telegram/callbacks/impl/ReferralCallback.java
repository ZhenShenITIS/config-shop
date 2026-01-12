package tg.configshop.telegram.callbacks.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.model.BotUser;
import tg.configshop.services.ReferralService; // Твой интерфейс
import tg.configshop.services.UserService;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class ReferralCallback implements Callback {
    private final CallbackName callbackName = CallbackName.REFERRAL;
    private final ReferralService referralService;
    private final UserService userService;

    @Value("${TELEGRAM_BOT_USERNAME}")
    private String botUsername;

    @Value("${SUPPORT_USERNAME}")
    private String supportUsername;

    @Override
    public CallbackName getCallback() {
        return callbackName;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long userId = callbackQuery.getFrom().getId();
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();

        int allCount = referralService.getAllReferralCount(userId);
        int activeCount = referralService.getActiveReferralCount(userId);
        long profit = referralService.getAllProfit(userId);
        long available = referralService.getAvailableSumToWithdraw(userId);
        String promoCode = referralService.getReferralPromoCode(userId);
        BotUser botUser = userService.getUser(userId);

        int lvl1 = botUser.getReferralPercentage1lvl();

        int lvl2 = botUser.getReferralPercentage2lvl();
        int lvl3 = botUser.getReferralPercentage3lvl();


        String refLink = String.format("https://t.me/%s?start=%d", botUsername, userId);

        String text = MessageText.REFERRAL_MENU.getMessageText().formatted(
                allCount,
                activeCount,
                profit,
                available,
                promoCode,
                lvl1,
                lvl2,
                lvl3,
                refLink
        );


        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.REF_LIST.getText())
                                .callbackData(CallbackName.REF_LIST.getCallbackName())
                                .build(),
                        InlineKeyboardButton.builder()
                                .text(ButtonText.WITHDRAW.getText())
                                .url("t.me/" + supportUsername)
                                // TODO
                                //.callbackData(CallbackName.WITHDRAW.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
                                .build()
                ))
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(markup)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
