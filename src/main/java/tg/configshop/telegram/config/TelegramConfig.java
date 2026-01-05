package tg.configshop.telegram.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TelegramConfig {
    @Value("${TELEGRAM_BOT_USERNAME}")
    String botName;
    @Value("${TELEGRAM_BOT_TOKEN}")
    String botToken;

    @Bean
    public TelegramClient telegramClient () {
        return new OkHttpTelegramClient(getBotToken());
    }



}
