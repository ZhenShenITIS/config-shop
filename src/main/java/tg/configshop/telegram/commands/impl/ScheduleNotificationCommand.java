package tg.configshop.telegram.commands.impl;

import tg.configshop.services.ExternalSubscriptionService;

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
import tg.configshop.telegram.TelegramUserService;
import tg.configshop.telegram.commands.Command;


@Component
@RequiredArgsConstructor
public class ScheduleNotificationCommand implements Command {
    private final TelegramUserService userService;
    private final SchedulerService schedulerService;
    private final ExternalSubscriptionService externalSubscriptionService;

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

        var accountId = userService.accountId(botUser.getId());
        var subscriptionId = userService.subscriptionId(botUser.getId());
        schedulerService.scheduleSubscriptionNotifications(accountId, subscriptionId, externalSubscriptionService.getExternalUser(accountId, subscriptionId).expireAt());
        telegramClient.execute(SendMessage
                .builder()
                .chatId(message.getChatId())
                .text("Успешно!")
                .build());

    }
}
