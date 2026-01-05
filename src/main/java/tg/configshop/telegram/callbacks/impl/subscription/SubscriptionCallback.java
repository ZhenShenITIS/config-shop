package tg.configshop.telegram.callbacks.impl.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import tg.configshop.services.UserService;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.util.DateUtil;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SubscriptionCallback implements Callback {
    private final CallbackName callbackName = CallbackName.SUBSCRIPTION;
    private final UserService userService;
    private final RemnawaveClient remnawaveClient;

    @Value("${CONFIG_PANEL_SUB_URL}")
    private String subUrl;

    @Override
    public CallbackName getCallback() {
        return callbackName;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();
        BotUser botUser = userService.getUser(userId);


        List<Device> devices = remnawaveClient.getUserDevices(botUser.getRemnawaveUuid());
        RemnawaveUserResponse remoteUser = remnawaveClient.getUser(botUser.getRemnawaveUuid());
        int maxDevices = remoteUser.hwidDeviceLimit();

        double usedTrafficGb = remoteUser.userTraffic().usedTrafficBytes() / (1024.0 * 1024 * 1024);
        double limitTrafficGb = remoteUser.trafficLimitBytes() / (1024.0 * 1024 * 1024);

        String devicesText;
        if (devices.isEmpty()) {
            devicesText = MessageText.EMPTY_DEVICES.getMessageText();
        } else {
            devicesText = devices.stream()
                    .map(device -> String.format("• %s — %s", device.platform(), device.deviceModel()))
                    .collect(Collectors.joining("\n"));
        }

        String statusEmoji = DateUtil.isExpired(botUser) ? "🔴" : "🟢";
        String dateEnd = DateUtil.getDateEndSubscription(botUser);
        long daysLeft = DateUtil.getDaysLeft(botUser);
        String subLink = subUrl + "/" + botUser.getShortId();

        String text = MessageText.SUBSCRIPTION.getMessageText().formatted(
                callbackQuery.getFrom().getFirstName(),
                botUser.getBalance(),
                statusEmoji, dateEnd, daysLeft,
                usedTrafficGb, limitTrafficGb,
                devices.size(), maxDevices,
                devicesText,
                subLink, subLink
        );


        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.CONNECT.getText())
                                .url(subLink)
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BUY_SUB.getText())
                                .callbackData(CallbackName.BUY_SUB_MENU.getCallbackName()) // Заглушка
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.BACK_TO_MENU.getCallbackName())
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
