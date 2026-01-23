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
import tg.configshop.dto.RemnawaveUser;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.services.DeviceService;
import tg.configshop.services.ExternalSubscriptionService;
import tg.configshop.services.UserService;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.util.DateUtil;
import tg.configshop.util.RsaEncryptor;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SubscriptionCallback implements Callback {
    private final CallbackName callbackName = CallbackName.SUBSCRIPTION;
    private final UserService userService;
    private final DeviceService deviceService;
    private final ExternalSubscriptionService externalSubscriptionService;

    @Value("${CONFIG_PANEL_SUB_URL}")
    private String subUrl;

    @Value("${INSTRUCTION_URL}")
    private String instructionUrl;

    @Override
    public CallbackName getCallback() {
        return callbackName;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();
        BotUser botUser = userService.getUser(userId);

        List<Device> devices = deviceService.getDevicesByUserId(userId);
        RemnawaveUser remoteUser = externalSubscriptionService.getExternalUserWithCryptLinkAndSync(userId);
        int maxDevices = remoteUser.hwidDeviceLimit();

        double usedTrafficGb = remoteUser.userTraffic().usedTraffic();
        double limitTrafficGb = remoteUser.trafficLimitGigaBytes();


        String devicesText;
        if (devices.isEmpty()) {
            devicesText = MessageText.EMPTY_DEVICES.getMessageText();
        } else {
            devicesText = devices.stream()
                    .map(device -> String.format("• %s — %s", device.platform(), device.deviceModel()))
                    .collect(Collectors.joining("\n"));
        }

        String statusEmoji = remoteUser.isActive() ? "🟢" : "🔴";
        String dateEnd = remoteUser.prettyDateExpireAt();
        long daysLeft = remoteUser.daysLeft();
        String temp = limitTrafficGb == 0 ? MessageText.SUBSCRIPTION.getMessageText() : MessageText.SUBSCRIPTION_TRIAL.getMessageText();
        String text = temp.formatted(
                callbackQuery.getFrom().getFirstName(),
                botUser.getId(),
                botUser.getBalance(),
                statusEmoji, dateEnd, daysLeft,
                usedTrafficGb, limitTrafficGb,
                devices.size(), maxDevices,
                devicesText,
                remoteUser.subscriptionUrl()
        ).replace("/ 0.00", "/ ∞");


        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.INSTRUCTION.getText())
                                .url(instructionUrl)
                                .build()
                ))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BUY_SUB.getText())
                                .callbackData(CallbackName.BUY_SUB_MENU.getCallbackName())
                                .build(),
                        InlineKeyboardButton.builder()
                                .text(ButtonText.DEVICES.getText())
                                .callbackData(CallbackName.DEVICES.getCallbackName())
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
