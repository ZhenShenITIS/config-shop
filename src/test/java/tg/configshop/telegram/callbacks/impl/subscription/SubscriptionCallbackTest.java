package tg.configshop.telegram.callbacks.impl.subscription;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.external_api.remnawave.dto.user.UserTraffic;
import tg.configshop.model.BotUser;
import tg.configshop.services.DeviceService;
import tg.configshop.services.ExternalSubscriptionService;
import tg.configshop.services.UserService;
import tg.configshop.util.RsaEncryptor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionCallbackTest {
    @Test
    void subscriptionDefaultsToJsonAndTogglesEncryptedLinkInSameMessage() throws Exception {
        String rawUrl = "https://example.com/subscription-id";
        String jsonKey = "happ://crypt4/json-key";
        String nonJsonKey = "happ://crypt4/non-json-key";
        long userId = 42L;
        BotUser botUser = BotUser.builder()
                .id(userId)
                .remnawaveUuid("remote-user")
                .expireAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        RemnawaveUserResponse response = RemnawaveUserResponse.builder()
                .uuid(botUser.getRemnawaveUuid())
                .telegramId(userId)
                .subscriptionUrl(rawUrl)
                .expireAt(botUser.getExpireAt())
                .hwidDeviceLimit(3)
                .trafficLimitBytes(0L)
                .userTraffic(new UserTraffic(0L, 0L))
                .build();

        UserService userService = mock(UserService.class);
        DeviceService deviceService = mock(DeviceService.class);
        RemnawaveClient remnawaveClient = mock(RemnawaveClient.class);
        TelegramClient telegramClient = mock(TelegramClient.class);
        when(userService.getUser(userId)).thenReturn(botUser);
        when(deviceService.getDevicesByUserId(userId)).thenReturn(List.of());
        when(remnawaveClient.getUser(botUser.remnawaveRef())).thenReturn(response);

        SubscriptionCallback callback = new SubscriptionCallback(userService, deviceService,
                new ExternalSubscriptionService(remnawaveClient, userService));
        ReflectionTestUtils.setField(callback, "instructionUrl", "https://example.com/instructions");

        User user = new User(userId, "Test", false);
        Message message = mock(Message.class);
        when(message.getChatId()).thenReturn(userId);
        when(message.getMessageId()).thenReturn(10);
        CallbackQuery query = new CallbackQuery();
        query.setFrom(user);
        query.setMessage(message);
        query.setData(CallbackName.SUBSCRIPTION.getCallbackName());

        try (MockedStatic<RsaEncryptor> encryptor = mockStatic(RsaEncryptor.class)) {
            encryptor.when(() -> RsaEncryptor.encryptAndBuildLink(rawUrl + "/json")).thenReturn(jsonKey);
            encryptor.when(() -> RsaEncryptor.encryptAndBuildLink(rawUrl)).thenReturn(nonJsonKey);

            callback.processCallback(query, telegramClient);
            ArgumentCaptor<EditMessageText> edits = ArgumentCaptor.forClass(EditMessageText.class);
            verify(telegramClient).execute(edits.capture());
            EditMessageText jsonMessage = edits.getValue();
            assertTrue(jsonMessage.getText().contains(jsonKey));
            InlineKeyboardButton nonJsonButton = jsonMessage.getReplyMarkup().getKeyboard().get(2).getFirst();
            assertEquals("🔑 Получить non-JSON ключ", nonJsonButton.getText());

            query.setData(nonJsonButton.getCallbackData());
            callback.processCallback(query, telegramClient);
            verify(telegramClient, times(2)).execute(edits.capture());
            EditMessageText nonJsonMessage = edits.getValue();
            assertEquals(jsonMessage.getText().replace(jsonKey, nonJsonKey), nonJsonMessage.getText());
            InlineKeyboardButton jsonButton = nonJsonMessage.getReplyMarkup().getKeyboard().get(2).getFirst();
            assertEquals("🔑 Получить JSON ключ", jsonButton.getText());

            query.setData(jsonButton.getCallbackData());
            callback.processCallback(query, telegramClient);
            verify(telegramClient, times(3)).execute(edits.capture());
            assertEquals(jsonMessage.getText(), edits.getValue().getText());
            encryptor.verify(() -> RsaEncryptor.encryptAndBuildLink(rawUrl + "/json"), times(2));
            encryptor.verify(() -> RsaEncryptor.encryptAndBuildLink(rawUrl));
            verify(userService, times(3)).syncRemnawaveUserWithLocalUser(response, botUser);

            for (EditMessageText edit : edits.getAllValues()) {
                assertEquals("42", edit.getChatId());
                assertEquals(10, edit.getMessageId());
                assertEquals("HTML", edit.getParseMode());
                assertEquals(4, edit.getReplyMarkup().getKeyboard().size());
                assertEquals(1, edit.getReplyMarkup().getKeyboard().get(2).size());
                assertEquals(CallbackName.BUY_MENU.getCallbackName(),
                        edit.getReplyMarkup().getKeyboard().get(1).get(0).getCallbackData());
                assertEquals(CallbackName.DEVICES.getCallbackName(),
                        edit.getReplyMarkup().getKeyboard().get(1).get(1).getCallbackData());
            }
        }
    }
}
