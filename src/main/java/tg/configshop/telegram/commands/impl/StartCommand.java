package tg.configshop.telegram.commands.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.commands.Command;
import tg.configshop.constants.CommandName;
import tg.configshop.telegram.dto.BotMessageParams;
import tg.configshop.telegram.message_body.StartCommandBody;
import tg.configshop.util.MessageUtils;


@Component
@RequiredArgsConstructor
public class StartCommand implements Command {
    private final CommandName commandName = CommandName.START;
    private final StartCommandBody startCommandBody;

    @Override
    public CommandName getCommand() {
        return commandName;
    }

    @Override
    public void handleCommand(Message message, TelegramClient telegramClient) {
        BotMessageParams params = startCommandBody.getMessage(message.getFrom(), MessageUtils.getReferrerId(message));
        SendMessage sendMessage = SendMessage
                .builder()
                .chatId(message.getChatId())
                .replyMarkup(params.inlineKeyboard())
                .text(params.text())
                .parseMode("HTML")
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }


}
