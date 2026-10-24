package tg.configshop.telegram;

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
import tg.configshop.telegram.TelegramUserService;
import tg.configshop.util.StringUtil;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReferralCreatedListener {
    private final TelegramClient telegramClient;
    private final TelegramUserService userService;
    private final ReferralService referralService;

    @org.springframework.transaction.event.TransactionalEventListener(phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
    public void handle (ReferralCreatedEvent event) {
        var recipient = userService.findByAccountId(event.referrerAccountId());
        if (recipient.isEmpty()) return;
        var source = userService.findByAccountId(event.referralAccountId());
        BotUser botUser = source.orElse(null);
        String safeName = StringUtil.getSafeHtmlString(userService.displayName(event.referralAccountId()));
        Optional<String> promoCode = referralService.getUsedPromo(event.referralAccountId());
        String text;
        if (promoCode.isPresent()) {
            text = MessageText.REFERRAL_CREATED_PROMO.getMessageText().formatted((botUser == null ? event.referralAccountId() : botUser.getId()), safeName, promoCode.get());
        } else {
            text = MessageText.REFERRAL_CREATED_LINK.getMessageText().formatted((botUser == null ? event.referralAccountId() : botUser.getId()), safeName);
        }

        if (botUser == null) {
            text = "👥 <b>Новый реферал!</b>\n\n" + safeName + " стал Вашим рефералом."
                    + promoCode.map(code -> " Промокод: " + StringUtil.getSafeHtmlString(code)).orElse("");
        }
        SendMessage sendMessage = SendMessage.builder()
                .chatId(recipient.get().getId())
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
