package tg.configshop.telegram.callbacks.impl.subscription;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.services.SubscriptionService;
import static org.mockito.Mockito.*;

class LegacyPurchaseButtonTest {
    private final SubscriptionService subscriptions = mock(SubscriptionService.class);
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
        new PurchaseCallback(subscriptions).processCallback(callback("123"), telegram);
        verify(subscriptions, never()).buySubscription(anyLong(), anyInt(), anyInt());
        verify(telegram).execute(any(EditMessageText.class));
        verify(subscriptions).getBaseSubscriptionCostByDays(30);
    }
    @Test void newButtonPassesPeriodAndDeviceCount() throws Exception {
        new PurchaseCallback(subscriptions).processCallback(callback("30:2"), telegram);
        verify(subscriptions).buySubscription(10L, 30, 2);
    }
}
