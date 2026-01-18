package tg.configshop.telegram.commands.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminOnly;
import tg.configshop.constants.CommandName;
import tg.configshop.services.UserService;
import tg.configshop.telegram.commands.Command;

@RequiredArgsConstructor
@Component
public class TopUpCommand implements Command {
    private final UserService userService;

    @Override
    public CommandName getCommand() {
        return CommandName.TOP_UP;
    }

    @SneakyThrows
    @Override
    @AdminOnly
    public void handleCommand(Message message, TelegramClient telegramClient) {
        try {
            String[] parts = message.getText().split(" ");
            long userId = Long.parseLong(parts[1]);
            long amount = Long.parseLong(parts[2]);
            userService.addToBalanceAsAdminTopUp(userId, amount);
            SendMessage sendMessage = SendMessage
                    .builder()
                    .chatId(message.getChatId())
                    .text("Успех")
                    .build();
            telegramClient.execute(sendMessage);
        } catch (RuntimeException e) {
            SendMessage sendMessage = SendMessage
                    .builder()
                    .chatId(message.getChatId())
                    .text("Произошла ошибка: " + e.getMessage())
                    .build();
            telegramClient.execute(sendMessage);
        }

    }
}
