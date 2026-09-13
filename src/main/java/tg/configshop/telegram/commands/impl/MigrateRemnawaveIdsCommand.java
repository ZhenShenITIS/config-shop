package tg.configshop.telegram.commands.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminOnly;
import tg.configshop.constants.CommandName;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.services.RemnawaveIdMigrationService;
import tg.configshop.telegram.commands.Command;

@Slf4j
@Component
@RequiredArgsConstructor
public class MigrateRemnawaveIdsCommand implements Command {
    private final RemnawaveIdMigrationService migrationService;
    private final RemnawaveApiVersion apiVersion;

    @Override
    public CommandName getCommand() {
        return CommandName.MIGRATE_REMNAWAVE_IDS;
    }

    @Override
    @AdminOnly
    public void handleCommand(Message message, TelegramClient telegramClient) {
        if (!apiVersion.isV2()) {
            send(message.getChatId(), telegramClient, "Заполнение ID доступно только в режиме API v2.");
            return;
        }
        send(message.getChatId(), telegramClient, "Начинаю заполнение Remnawave ID.");
        String report = migrationService.migrate().report();
        for (int offset = 0; offset < report.length(); offset += 4000) {
            send(message.getChatId(), telegramClient, report.substring(offset, Math.min(offset + 4000, report.length())));
        }
    }

    private void send(Long chatId, TelegramClient client, String text) {
        try {
            client.execute(SendMessage.builder().chatId(chatId).text(text).build());
        } catch (TelegramApiException e) {
            log.warn("Failed to send Remnawave ID migration report to admin chat {}", chatId);
        }
    }
}
