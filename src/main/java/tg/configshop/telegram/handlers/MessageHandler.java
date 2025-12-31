package tg.configshop.telegram.handlers;


import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;


public interface MessageHandler {
    void answerMessage(Message message, TelegramClient telegramClient);
}
