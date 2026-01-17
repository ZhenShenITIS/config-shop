package tg.configshop.telegram.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.updates.GetWebhookInfo;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "start.webhook", havingValue = "true")
public class WebhookConfig {

    private final TelegramConfig telegramConfig;

    @PostConstruct
    public void registerWebhook() {
        try {
            String fullWebhookUrl = telegramConfig.getWebhookUrl() + "/webhook";

            OkHttpTelegramClient client = new OkHttpTelegramClient(
                    telegramConfig.getBotToken()
            );

            SetWebhook setWebhook = SetWebhook.builder()
                    .url(fullWebhookUrl)
                    .build();

            Boolean result = client.execute(setWebhook);

            if (Boolean.TRUE.equals(result)) {
                System.out.println("✅ Webhook registered successfully: " + fullWebhookUrl);
            } else {
                System.err.println("❌ Webhook registration FAILED: " + fullWebhookUrl);
                System.err.println("Result: " + result);

                String info = String.valueOf(client.execute(new GetWebhookInfo()));
                System.err.println("Current webhook info: " + info);
            }
        } catch (TelegramApiException e) {
            System.err.println("❌ Exception during webhook registration:");
            e.printStackTrace();
            throw new RuntimeException("Failed to register webhook", e);
        }
    }
}
