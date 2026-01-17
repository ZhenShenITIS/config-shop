package tg.configshop.telegram.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "START_WEBHOOK", havingValue = "true")
public class WebhookController {
    private final WebhookBot webhookBot;


    @PostMapping("/webhook")
    public BotApiMethod<?> onUpdateReceived(@RequestBody Update update) {
        Thread.startVirtualThread(() -> webhookBot.processUpdate(update));
        return null;
    }


}
