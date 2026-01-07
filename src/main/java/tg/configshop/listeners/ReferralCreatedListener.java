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
import tg.configshop.services.UserService;
import tg.configshop.util.StringUtil;

@Component
@RequiredArgsConstructor
public class ReferralCreatedListener {
    private final TelegramClient telegramClient;
    private final UserService userService;

    @EventListener
    public void handle (ReferralCreatedEvent event) {
        BotUser botUser = userService.getUser(event.getReferralId());
        String safeName = StringUtil.getSafeHtmlString(botUser.getFirstName());
        String text = MessageText.REFERRAL_CREATED.getMessageText().formatted(botUser.getId(), safeName);
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
