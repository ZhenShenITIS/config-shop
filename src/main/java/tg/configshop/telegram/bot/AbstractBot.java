package tg.configshop.telegram.bot;

import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

@Slf4j
public abstract class AbstractBot {
    protected void extractLog(Update update, Exception e) {

        User user = null;
        if (update.hasMessage()) {
            user = update.getMessage().getFrom();
        } else if (update.hasCallbackQuery()) {
            user = update.getCallbackQuery().getFrom();
        }

        log.atError()
                .setCause(e)
                .setMessage("Ошибка при обработке апдейта")
                .addKeyValue("update_id", update.getUpdateId())
                .addKeyValue("user_id", user != null ? user.getId() : null)
                .addKeyValue("username", user != null ? user.getUserName() : null)
                .log();
    }
}
