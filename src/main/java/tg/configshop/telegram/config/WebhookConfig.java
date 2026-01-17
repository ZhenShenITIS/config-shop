package tg.configshop.telegram.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "START_WEBHOOK", havingValue = "true")
public class WebhookConfig {

    private final TelegramConfig telegramConfig;

    @PostConstruct
    public void registerWebhook() {
        try {
            String webhookUrl = telegramConfig.getWebhookUrl() + "/webhook";

            OkHttpTelegramClient client = new OkHttpTelegramClient(
                    telegramConfig.getBotToken()
            );

            SetWebhook setWebhook = SetWebhook.builder()
                    .url(webhookUrl)
                    .build();

            client.execute(setWebhook);

            System.out.println("Webhook registered: " + webhookUrl);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Failed to register webhook", e);
        }
    }
}
