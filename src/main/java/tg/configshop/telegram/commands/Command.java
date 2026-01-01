package tg.configshop.telegram.commands;


import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CommandName;


public interface Command {
    CommandName getCommand ();
    void handleCommand(Message message, TelegramClient telegramClient);
}
