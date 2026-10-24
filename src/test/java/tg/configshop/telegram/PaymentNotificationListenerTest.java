package tg.configshop.telegram;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.events.PaymentConfirmedEvent;
import tg.configshop.model.BotUser;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentNotificationListenerTest {
    private final TelegramUserService users = mock(TelegramUserService.class);
    private final TelegramClient telegram = mock(TelegramClient.class);
    private final PaymentConfirmedEvent event = new PaymentConfirmedEvent(UUID.randomUUID(), 1000, UUID.randomUUID());
    private AnnotationConfigApplicationContext context;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        var manager = new JdbcTransactionManager(new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", ""));
        transaction = new TransactionTemplate(manager);
        when(users.findByAccountId(event.accountId())).thenReturn(Optional.of(BotUser.builder().telegramId(10L).build()));
        context = new AnnotationConfigApplicationContext();
        context.registerBean(PlatformTransactionManager.class, () -> manager);
        context.registerBean(TelegramUserService.class, () -> users);
        context.registerBean(TelegramClient.class, () -> telegram);
        context.register(Config.class);
        context.refresh();
    }

    @AfterEach void close() {
        context.close();
    }

    @Test void sendsAmountAndMenuButtonOnlyAfterCommit() throws Exception {
        transaction.executeWithoutResult(tx -> {
            context.publishEvent(event);
            verifyNoInteractions(users, telegram);
        });
        var message = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegram, timeout(2000)).execute(message.capture());
        assertEquals("10", message.getValue().getChatId());
        assertEquals("HTML", message.getValue().getParseMode());
        assertTrue(message.getValue().getText().contains("✅ <b>Оплата зачислена</b>"));
        assertTrue(message.getValue().getText().contains("<b>1000 ₽</b>"));
        var keyboard = assertInstanceOf(InlineKeyboardMarkup.class, message.getValue().getReplyMarkup());
        var button = keyboard.getKeyboard().getFirst().getFirst();
        assertEquals(ButtonText.BACK_TO_MENU.getText(), button.getText());
        assertEquals(CallbackName.BACK_TO_MENU.getCallbackName(), button.getCallbackData());
    }

    @Test void doesNotNotifyWhenPaymentTransactionRollsBack() {
        transaction.executeWithoutResult(tx -> {
            context.publishEvent(event);
            tx.setRollbackOnly();
        });
        verifyNoInteractions(users, telegram);
    }

    @Test void doesNotNotifyForEventPublishedOutsideTransaction() {
        context.publishEvent(event);
        verifyNoInteractions(users, telegram);
    }

    @Test void skipsAccountWithoutTelegramProfile() {
        when(users.findByAccountId(event.accountId())).thenReturn(Optional.empty());
        transaction.executeWithoutResult(tx -> context.publishEvent(event));
        verify(users, timeout(2000)).findByAccountId(event.accountId());
        verifyNoInteractions(telegram);
    }

    @Test void deliveryFailureIsHandledByNotificationListener() throws Exception {
        doThrow(new TelegramApiException("bot blocked")).when(telegram).execute(any(SendMessage.class));
        var listener = new PaymentNotificationListener(users, telegram);
        assertDoesNotThrow(() -> listener.handle(event));
        verify(telegram).execute(any(SendMessage.class));
    }

    @Configuration
    @EnableAsync
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import(PaymentNotificationListener.class)
    static class Config {}
}
