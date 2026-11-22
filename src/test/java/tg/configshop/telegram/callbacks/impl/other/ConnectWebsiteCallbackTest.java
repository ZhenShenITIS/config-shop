package tg.configshop.telegram.callbacks.impl.other;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tg.configshop.constants.DialogStageName;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.repositories.WebUserRepository;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.model.Account;
import tg.configshop.model.BotUser;
import tg.configshop.telegram.TelegramUserService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ConnectWebsiteCallbackTest {
    private final TelegramUserService users = mock(TelegramUserService.class);
    private final UserStateRepository states = mock(UserStateRepository.class);
    private final WebUserRepository webUsers = mock(WebUserRepository.class);
    private final TelegramClient client = mock(TelegramClient.class);
    private final ConnectWebsiteCallback callback = new ConnectWebsiteCallback(users, states, webUsers);

    @Test
    void asksForCodeOnlyInPrivateChat() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(users.getUser(42L)).thenReturn(BotUser.builder()
                .account(Account.builder().id(accountId).build()).build());

        callback.processCallback(query(42L), client);

        var sent = ArgumentCaptor.forClass(SendMessage.class);
        verify(client).execute(sent.capture());
        assertEquals("42", sent.getValue().getChatId());
        assertTrue(sent.getValue().getText().contains("шестизначный код"));
        verify(states).put(42L, DialogStageName.WEBSITE_CODE);
    }

    @Test
    void refusesToAskForCodeInGroupChat() throws Exception {
        callback.processCallback(query(-10042L), client);

        verifyNoInteractions(users, states, webUsers);
        verify(client).execute(any(AnswerCallbackQuery.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void oldButtonReportsAlreadyLinkedWithoutEnteringCodeStage() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(users.getUser(42L)).thenReturn(BotUser.builder()
                .account(Account.builder().id(accountId).build()).build());
        when(webUsers.existsByAccountId(accountId)).thenReturn(true);

        callback.processCallback(query(42L), client);

        var answer = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(client).execute(answer.capture());
        assertEquals("Сайт уже подключён.", answer.getValue().getText());
        verifyNoInteractions(states);
        verifyNoMoreInteractions(client);
    }

    private CallbackQuery query(long chatId) {
        Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(chatId);
        CallbackQuery query = new CallbackQuery();
        query.setId("test-callback");
        query.setFrom(new User(42L, "Test", false));
        query.setMessage(message);
        return query;
    }
}
