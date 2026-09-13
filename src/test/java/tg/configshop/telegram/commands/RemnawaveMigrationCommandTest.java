package tg.configshop.telegram.commands;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminAspect;
import tg.configshop.constants.CommandName;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.repositories.AdministratorRepository;
import tg.configshop.services.RemnawaveIdMigrationService;
import tg.configshop.telegram.commands.impl.MigrateRemnawaveIdsCommand;
import tg.configshop.telegram.containers.CommandContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemnawaveMigrationCommandTest {
    private final RemnawaveIdMigrationService service = mock(RemnawaveIdMigrationService.class);
    private final AdministratorRepository admins = mock(AdministratorRepository.class);
    private final TelegramClient telegram = mock(TelegramClient.class);

    @Test
    void nonAdminCannotRunMigration() {
        proxy(2).handleCommand(message(), telegram);
        verifyNoInteractions(service, telegram);
    }

    @Test
    void adminCommandIsRegisteredAndSendsSummary() throws Exception {
        when(admins.isAdmin(736L)).thenReturn(true);
        when(service.migrate()).thenReturn(new RemnawaveIdMigrationService.Result(736, 736, 0, List.of()));
        Command command = proxy(2);
        var container = new CommandContainer(List.of(command));
        assertSame(command, container.retrieveCommand("/migrate_remnawave_ids"));
        assertEquals(CommandName.MIGRATE_REMNAWAVE_IDS, command.getCommand());
        command.handleCommand(message(), telegram);
        var messages = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegram, times(2)).execute(messages.capture());
        assertTrue(messages.getAllValues().getLast().getText().contains("Заполнено: 736"));
    }

    @Test
    void commandRefusesV3() throws Exception {
        when(admins.isAdmin(736L)).thenReturn(true);
        proxy(3).handleCommand(message(), telegram);
        verifyNoInteractions(service);
        verify(telegram).execute(any(SendMessage.class));
    }

    @Test
    void longReportIsSplitWithoutDroppingCharacters() throws Exception {
        when(admins.isAdmin(736L)).thenReturn(true);
        var result = new RemnawaveIdMigrationService.Result(736, 0, 736, List.of("x".repeat(9000)));
        when(service.migrate()).thenReturn(result);
        proxy(2).handleCommand(message(), telegram);
        var messages = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegram, times(4)).execute(messages.capture());
        var chunks = messages.getAllValues().subList(1, 4);
        assertTrue(chunks.stream().allMatch(m -> m.getText().length() <= 4000));
        assertEquals(result.report(), chunks.stream().map(SendMessage::getText).reduce("", String::concat));
    }

    private Command proxy(int version) {
        var factory = new AspectJProxyFactory(new MigrateRemnawaveIdsCommand(service, new RemnawaveApiVersion(version)));
        factory.addAspect(new AdminAspect(admins));
        return factory.getProxy();
    }

    private Message message() {
        Message message = mock(Message.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(736L);
        when(message.getFrom()).thenReturn(user);
        when(message.getChatId()).thenReturn(736L);
        return message;
    }
}
