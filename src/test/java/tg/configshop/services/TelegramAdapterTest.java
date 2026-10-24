package tg.configshop.services;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.dto.*;
import tg.configshop.model.*;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.telegram.*;
import tg.configshop.telegram.callbacks.impl.devices.PreviewDevicePurchaseCallback;
import tg.configshop.telegram.callbacks.impl.devices.ConfirmDevicePurchaseCallback;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramAdapterTest {
    private final UUID accountId = UUID.randomUUID(), subscriptionId = UUID.randomUUID();

    @Test void singleSubscriptionAdapterHandlesZeroAndManyExplicitly() {
        var repository = mock(BotUserRepository.class);
        var access = mock(SubscriptionAccessService.class);
        when(repository.findById(10L)).thenReturn(Optional.of(BotUser.builder().telegramId(10L)
                .account(Account.builder().id(accountId).build()).build()));
        var users = new TelegramUserService(repository, mock(BalanceService.class), access);
        when(access.list(accountId)).thenReturn(List.of());
        assertThrows(SubscriptionSelectionException.class, () -> users.subscriptionId(10L));
        when(access.list(accountId)).thenReturn(List.of(new SubscriptionSummary(subscriptionId, accountId, "first")));
        assertEquals(subscriptionId, users.subscriptionId(10L));
        when(access.list(accountId)).thenReturn(List.of(new SubscriptionSummary(subscriptionId, accountId, "first"),
                new SubscriptionSummary(UUID.randomUUID(), accountId, "second")));
        assertThrows(SubscriptionSelectionException.class, () -> users.subscriptionId(10L));
    }

    @Test void previewIgnoresPriceStoredInCallback() throws Exception {
        var users = users();
        var devices = mock(DevicePurchaseService.class);
        when(devices.preview(accountId, subscriptionId, 2)).thenReturn(new DevicePurchasePreview(50, 100, 5, 1));
        var telegram = mock(TelegramClient.class);
        new PreviewDevicePurchaseCallback(devices, users).processCallback(query(CallbackName.PREVIEW_DEVICE_PURCHASE), telegram);
        var edit = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegram).execute(edit.capture());
        assertEquals(CallbackName.CONFIRM_DEVICE_PURCHASE.getCallbackName() + ":2:50",
                edit.getValue().getReplyMarkup().getKeyboard().getFirst().getFirst().getCallbackData());
        verify(devices, never()).purchaseDevices(any(), any(), anyInt());
    }

    @Test void confirmationPassesOnlyIdentityAndCountToPurchase() throws Exception {
        var users = users();
        var devices = mock(DevicePurchaseService.class);
        when(devices.preview(accountId, subscriptionId, 2)).thenReturn(new DevicePurchasePreview(50, 100, 5, 1));
        when(devices.purchaseDevices(accountId, subscriptionId, 2)).thenReturn(100L);
        var telegram = mock(TelegramClient.class);
        new ConfirmDevicePurchaseCallback(devices, mock(SubscriptionService.class), users)
                .processCallback(query(CallbackName.CONFIRM_DEVICE_PURCHASE), telegram);
        verify(devices).purchaseDevices(accountId, subscriptionId, 2);
        var edit = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegram).execute(edit.capture());
        assertTrue(edit.getValue().getText().contains("100"));
    }

    private TelegramUserService users() {
        var users = mock(TelegramUserService.class);
        when(users.accountId(10L)).thenReturn(accountId);
        when(users.subscriptionId(10L)).thenReturn(subscriptionId);
        return users;
    }
    private CallbackQuery query(CallbackName name) {
        var query = new CallbackQuery(); query.setFrom(new User(10L, "Test", false));
        var message = mock(Message.class); when(message.getChatId()).thenReturn(10L); when(message.getMessageId()).thenReturn(1);
        query.setMessage(message); query.setData(name.getCallbackName() + ":2:1");
        return query;
    }
}
