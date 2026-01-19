package tg.configshop.telegram.callbacks.impl.referrals;

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

import java.util.List;

@Component
@RequiredArgsConstructor
public class ReferralCallback implements Callback {
    private final CallbackName callbackName = CallbackName.REFERRAL;
    private final ReferralService referralService;
    private final UserService userService;

    @Value("${TELEGRAM_BOT_USERNAME}")
    private String botUsername;

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
        List<String> promoCode = referralService.getReferralPromoCodes(userId);
        BotUser botUser = userService.getUser(userId);

        int lvl1 = botUser.getReferralPercentage1lvl();

        int lvl2 = botUser.getReferralPercentage2lvl();
        int lvl3 = botUser.getReferralPercentage3lvl();

        String messageTemplate =
                promoCode.size() > 1 ?
                        MessageText.REFERRAL_MENU_MANY_CODES.getMessageText() :
                        MessageText.REFERRAL_MENU.getMessageText();

        String refLink = String.format("https://t.me/%s?start=%d", botUsername, userId);

        String text = messageTemplate.formatted(
                allCount,
                activeCount,
                profit,
                available,
                getPromoAsString(promoCode),
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
                                .callbackData(CallbackName.WITHDRAW.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.ADD_PROMO.getText())
                                .callbackData(CallbackName.ADD_REF_PROMO.getCallbackName())
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


    private String getPromoAsString (List<String> promoCodes) {
        StringBuilder sb = new StringBuilder();
        for (String s : promoCodes) {
            sb.append("\n<code>");
            sb.append(s);
            sb.append("</code>");
        }
        return sb.toString();
    }
}
