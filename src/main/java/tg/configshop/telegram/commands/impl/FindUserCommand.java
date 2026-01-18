package tg.configshop.telegram.commands.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminOnly;
import tg.configshop.constants.CommandName;
import tg.configshop.model.BotUser;
import tg.configshop.services.UserService;
import tg.configshop.telegram.commands.Command;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FindUserCommand implements Command {
    private final UserService userService;
    private final ObjectMapper objectMapper;


    @Override
    public CommandName getCommand() {
        return CommandName.FIND_USER;
    }

    @Override
    @AdminOnly
    public void handleCommand(Message message, TelegramClient telegramClient) {

        String textToSend;
        String msgPayload = message.getText().split(" ")[1];
        try {
            BotUser botUser = userService.getUser(Long.parseLong(msgPayload));
            textToSend = objectMapper.writeValueAsString(botUser);
        } catch (NumberFormatException e) {
            List<BotUser> botUsers = userService.getUser(msgPayload.replace("@", ""));
            try {
                textToSend = objectMapper.writeValueAsString(botUsers);
            } catch (JsonProcessingException ex) {
                textToSend = ex.toString();
            }
        } catch (JsonProcessingException e) {
            textToSend = e.toString();
        }

        long chatId = message.getChatId();
        SendMessage sendMessage = SendMessage
                .builder()
                .text(textToSend.substring(0, Math.min(textToSend.length(), 4095)))
                .chatId(chatId)
                .build();
        try {
            telegramClient.execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
        textToSend = textToSend.substring(Math.min(textToSend.length(), 4096), textToSend.length() - 1);
        while (textToSend.length() > 4096) {
            SendMessage newSendMessage = SendMessage
                    .builder()
                    .text(textToSend.substring(0, 4095))
                    .chatId(chatId)
                    .build();
            try {
                telegramClient.execute(newSendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            textToSend = textToSend.substring(4096, textToSend.length() - 1);
        }

    }
}
