package tg.configshop.config;

import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import tg.configshop.telegram.bot.ShopBot;
import tg.configshop.telegram.config.TelegramConfig;
import tg.configshop.telegram.containers.DialogStateContainer;
import tg.configshop.telegram.handlers.CallbackQueryHandler;
import tg.configshop.telegram.handlers.MessageHandler;


@Configuration
@AllArgsConstructor
@EnableAsync
public class SpringConfig {

//    @Bean
//    public ShopBot shopBot(TelegramConfig telegramConfig, CallbackQueryHandler callbackQueryHandler, MessageHandler messageHandler, DialogStateContainer dialogStateContainer) {
//        return new ShopBot(telegramConfig, callbackQueryHandler, messageHandler);
//
//    }
}