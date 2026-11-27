package tg.configshop.telegram.callbacks.impl.subscription;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.SubscriptionChangeStatus;
import tg.configshop.dto.SubscriptionChangeResult;
import org.mockito.ArgumentCaptor;
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.TelegramUserService;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        verify(subscriptions, never()).renewSubscription(any(), any(), any(), anyInt(), anyInt());
        verify(telegram).execute(any(EditMessageText.class));
        verify(subscriptions).getBaseSubscriptionCostByDays(30);
    }
    @Test void oldConfirmationReusesKeyOnRepeatedTap() {
        when(users.accountId(10L)).thenReturn(accountId);
        when(users.subscriptionId(10L)).thenReturn(subscriptionId);
        when(subscriptions.renewSubscription(any(), eq(accountId), eq(subscriptionId), eq(30), eq(2)))
                .thenReturn(new SubscriptionChangeResult(UUID.randomUUID(), 100, SubscriptionChangeStatus.COMPLETED));
        var handler = new PurchaseCallback(users, subscriptions);
        handler.processCallback(callback("30:2"), telegram);
        handler.processCallback(callback("30:2"), telegram);
        var keys = ArgumentCaptor.forClass(UUID.class);
        verify(subscriptions, times(2)).renewSubscription(keys.capture(), eq(accountId), eq(subscriptionId), eq(30), eq(2));
        assertEquals(keys.getAllValues().getFirst(), keys.getAllValues().getLast());
    }

    @Test void newConfirmationUsesSubscriptionAndKeyFromTheButton() {
        when(users.accountId(10L)).thenReturn(accountId);
        var id = UUID.randomUUID();
        when(subscriptions.renewSubscription(id, accountId, subscriptionId, 30, 2))
                .thenReturn(new SubscriptionChangeResult(id, 100, SubscriptionChangeStatus.COMPLETED));
        var encoded = "30:2:" + PurchaseButton.encode(id) + ":" + PurchaseButton.encode(subscriptionId);
        new PurchaseCallback(users, subscriptions).processCallback(callback(encoded), telegram);
        verify(subscriptions).renewSubscription(id, accountId, subscriptionId, 30, 2);
        verify(users, never()).subscriptionId(anyLong());
    }
}
