package tg.configshop.telegram.config;

import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class TelegramWebhookSecret {
    public static final String HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private final String value;

    public TelegramWebhookSecret(@Value("${TELEGRAM_BOT_TOKEN}") String botToken) {
        value = Hashing.sha256().hashString("config-shop:telegram-webhook:" + botToken,
                StandardCharsets.UTF_8).toString();
    }

    String value() {
        return value;
    }

    public boolean matches(String header) {
        return header != null && MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8),
                header.getBytes(StandardCharsets.UTF_8));
    }
}
