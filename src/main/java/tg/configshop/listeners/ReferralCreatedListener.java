package tg.configshop.listeners;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.events.ReferralCreatedEvent;
import tg.configshop.services.UserService;

@Component
@RequiredArgsConstructor
public class ReferralCreatedListener {
    private final TelegramClient telegramClient;
    private final UserService userService;

    @EventListener
    public void handle (ReferralCreatedEvent event) {
        SendMessage sendMessage = SendMessage.builder()
                .chatId(event.getReferrerId())
                .text("Новый реферал!\n"+userService.getUser(event.getReferralId()).getUsername())
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
