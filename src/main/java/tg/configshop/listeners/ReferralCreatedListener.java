package tg.configshop.listeners;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.MessageText;
import tg.configshop.events.ReferralCreatedEvent;
import tg.configshop.model.BotUser;
import tg.configshop.model.PromoCode;
import tg.configshop.services.ReferralService;
import tg.configshop.services.UserService;
import tg.configshop.util.StringUtil;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReferralCreatedListener {
    private final TelegramClient telegramClient;
    private final UserService userService;
    private final ReferralService referralService;

    @EventListener
    public void handle (ReferralCreatedEvent event) {
        BotUser botUser = userService.getUser(event.getReferralId());
        String safeName = StringUtil.getSafeHtmlString(botUser.getFirstName());
        Optional<PromoCode> promoCode = referralService.getUsedPromo(botUser.getId());
        String text;
        if (promoCode.isPresent()) {
            text = MessageText.REFERRAL_CREATED_PROMO.getMessageText().formatted(botUser.getId(), safeName, promoCode.get().getCode());
        } else {
            text = MessageText.REFERRAL_CREATED_LINK.getMessageText().formatted(botUser.getId(), safeName);
        }

        SendMessage sendMessage = SendMessage.builder()
                .chatId(event.getReferrerId())
                .text(text)
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
