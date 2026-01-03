package tg.configshop.telegram.message_body;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.model.BotUser;
import tg.configshop.services.RegistrationService;
import tg.configshop.services.UserService;
import tg.configshop.telegram.dto.BotMessageParams;
import tg.configshop.util.DateUtil;

@Component
@RequiredArgsConstructor
public class StartCommandBody {
    private final RegistrationService registrationService;
    private final UserService userService;

    @Value("${CONFIG_PANEL_SUB_URL}")
    private String subUrl;

    @Value("${SUPPORT_USERNAME}")
    private String supportUsername;

    public BotMessageParams getMessage (User user, Long referrerId) {
        long userId = user.getId();
        BotUser botUser;
        if (!registrationService.isRegistered(userId)) {
            botUser = registrationService.registerUser(user, referrerId);
        } else {
            botUser = userService.getUser(userId);
        }

        String text = DateUtil.isExpired(botUser) ?
                MessageText.START_TEXT_EXPIRED.getMessageText().formatted(user.getFirstName())
                :
                MessageText.START_TEXT_ACTIVE.getMessageText().formatted(user.getFirstName(), DateUtil.getDateEndSubscription(botUser));

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup
                .builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text(ButtonText.CONNECT.getText())
                                .url(subUrl+"/"+botUser.getShortId())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text(ButtonText.BALANCE.getText().formatted(botUser.getBalance()))
                                .callbackData(CallbackName.BALANCE.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text(ButtonText.SUBSCRIPTION.getText())
                                .callbackData(CallbackName.SUBSCRIPTION.getCallbackName())
                                .build(),
                        InlineKeyboardButton
                                .builder()
                                .text(ButtonText.PROMO_CODE.getText())
                                .callbackData(CallbackName.PROMO_CODE.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text(ButtonText.REFERRAL.getText())
                                .callbackData(CallbackName.REFERRAL.getCallbackName())
                                .build(),
                        InlineKeyboardButton
                                .builder()
                                .text(ButtonText.SUPPORT.getText())
                                .url("t.me/" + supportUsername)
                                .build()
                ))
                .build();

        return new BotMessageParams(text, keyboard);

    }

}
