package tg.configshop.telegram.callbacks.impl.subscription;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.TelegramUserService;
import java.util.UUID;
import static org.mockito.Mockito.*;

class LegacyPurchaseButtonTest {
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
    private final TelegramUserService users = mock(TelegramUserService.class);
    private final UUID accountId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final TelegramClient telegram = mock(TelegramClient.class);
    private CallbackQuery callback(String payload) {
        var query = mock(CallbackQuery.class);
        var user = mock(User.class);
        var message = mock(Message.class);
        when(query.getData()).thenReturn(CallbackName.PURCHASE.getCallbackName() + ":" + payload);
        when(query.getFrom()).thenReturn(user);
        when(user.getId()).thenReturn(10L);
        when(query.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(10L);
        when(message.getMessageId()).thenReturn(1);
        return query;
    }
    @Test void oldTariffIdReturnsToCurrentMenuWithoutCharging() throws Exception {
        new PurchaseCallback(users, subscriptions).processCallback(callback("123"), telegram);
        verify(subscriptions, never()).buySubscription(any(), any(), anyInt(), anyInt());
        verify(telegram).execute(any(EditMessageText.class));
        verify(subscriptions).getBaseSubscriptionCostByDays(30);
    }
    @Test void newButtonPassesPeriodAndDeviceCount() throws Exception {
        when(users.accountId(10L)).thenReturn(accountId);
        when(users.subscriptionId(10L)).thenReturn(subscriptionId);
        new PurchaseCallback(users, subscriptions).processCallback(callback("30:2"), telegram);
        verify(subscriptions).buySubscription(accountId, subscriptionId, 30, 2);
    }
}
