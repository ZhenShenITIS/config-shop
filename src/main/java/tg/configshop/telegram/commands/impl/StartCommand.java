package tg.configshop.telegram.commands.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.commands.Command;
import tg.configshop.constants.CommandName;
import tg.configshop.constants.MessageText;


@Component
public class StartCommand implements Command {
    CommandName commandName = CommandName.START;
    @Override
    public CommandName getCommand() {
        return commandName;
    }

    @Override
    public void handleCommand(Message message, TelegramClient telegramClient) {
        long chatId = message.getChatId();
        String textToSend = MessageText.START_TEXT.getMessageText();
        User user = message.getFrom();
        SendMessage sendMessage = SendMessage
                .builder()
                .text(textToSend)
                .chatId(chatId)
                .build();

        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }
}
