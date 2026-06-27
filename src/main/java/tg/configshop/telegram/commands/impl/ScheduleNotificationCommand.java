package tg.configshop.telegram.commands.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminOnly;
import tg.configshop.constants.CommandName;
import tg.configshop.model.BotUser;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.services.UserService;
import tg.configshop.telegram.commands.Command;


@Component
@RequiredArgsConstructor
public class ScheduleNotificationCommand implements Command {
    private final UserService userService;
    private final SchedulerService schedulerService;

    @Override
    public CommandName getCommand() {
        return CommandName.SCHEDULE_NOTIFICATION;
    }

    @SneakyThrows
    @Override
    @AdminOnly
    public void handleCommand(Message message, TelegramClient telegramClient) {

        String msgPayload = message.getText().split(" ")[1];
        BotUser botUser;
        try {
            botUser = userService.getUser(Long.parseLong(msgPayload));
        } catch (NumberFormatException e) {
            telegramClient.execute(SendMessage
                    .builder()
                            .chatId(message.getChatId())
                            .text("Ошибка парсинга")
                    .build());
            return;
        }

        schedulerService.scheduleSubscriptionNotifications(botUser.getId(), botUser.getExpireAt());
        telegramClient.execute(SendMessage
                .builder()
                .chatId(message.getChatId())
                .text("Успешно!")
                .build());

    }
}
