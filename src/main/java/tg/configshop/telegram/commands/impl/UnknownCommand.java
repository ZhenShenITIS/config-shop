package tg.configshop.telegram.commands.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.commands.Command;
import tg.configshop.telegram.constants.CommandName;


@Component
public class UnknownCommand implements Command {
    private CommandName commandName = CommandName.UNKNOWN;

    @Override
    public CommandName getCommand() {
        return commandName;
    }

    @Override
    public void handleCommand(Message message, TelegramClient telegramClient) {
        SendMessage sendMessage = SendMessage
                .builder()
                .text("")
                .build();
    }
}
