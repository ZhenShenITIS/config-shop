package tg.configshop.telegram.callbacks.impl.devices;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.services.DeviceService;
import tg.configshop.services.UserService;
import tg.configshop.telegram.callbacks.Callback;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DevicesCallback implements Callback {
    private final UserService userService;
    private final RemnawaveClient remnawaveClient;
    private final DeviceService deviceService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.DEVICES;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();
        BotUser botUser = userService.getUser(userId);

        List<Device> devices = deviceService.getDevicesByUserId(userId);
        RemnawaveUserResponse remoteUser = remnawaveClient.getUser(botUser.getRemnawaveUuid());
        int maxDevices = remoteUser.hwidDeviceLimit();

        String devicesText;
        if (devices.isEmpty()) {
            devicesText = MessageText.EMPTY_DEVICES.getMessageText();
        } else {
            devicesText = devices.stream()
                    .map(device -> String.format("• %s — %s", device.platform(), device.deviceModel()))
                    .collect(Collectors.joining("\n"));
        }

        String text = MessageText.DEVICES_MENU.getMessageText().formatted(
                devices.size(),
                maxDevices,
                devicesText
        );

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.DELETE_DEVICES.getText())
                                .callbackData(CallbackName.DELETE_DEVICES.getCallbackName())
                                .build(),
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BUY_MORE_DEVICES.getText())
                                .callbackData(CallbackName.BUY_MORE_DEVICES.getCallbackName())
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.SUBSCRIPTION.getCallbackName())
                                .build()
                ))
                .build();

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(callbackQuery.getMessage().getChatId())
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(text)
                .replyMarkup(markup)
                .parseMode("HTML")
                .disableWebPagePreview(true)
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
