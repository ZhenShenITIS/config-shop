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
import tg.configshop.external_api.remnawave.dto.device.Device;
import tg.configshop.services.DeviceService;
import tg.configshop.telegram.callbacks.Callback;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DeleteDevicesCallback implements Callback {
    private static final String PAYLOAD_SEPARATOR = ":";
    private static final int DEVICES_PER_PAGE = 5;

    private final DeviceService deviceService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.DELETE_DEVICES;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();
        String callbackData = callbackQuery.getData();

        String[] parts = callbackData.split(PAYLOAD_SEPARATOR);

        if (parts.length == 4 && parts[1].equals("hwid")) {
            String hwid = parts[2];
            int page = 0;
            try {
                page = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                page = 0;
            }
            deleteDeviceAndShowList(userId, hwid, page, callbackQuery, telegramClient);
            return;
        }

        int page = 0;
        if (parts.length == 2) {
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                page = 0;
            }
        }

        showDevicesList(userId, page, callbackQuery, telegramClient);
    }

    private void deleteDeviceAndShowList(Long userId, String hwid, int page,
                                         CallbackQuery callbackQuery, TelegramClient telegramClient) {
        try {
            deviceService.deleteDeviceById(userId, hwid);

            showDevicesList(userId, page, callbackQuery, telegramClient);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showDevicesList(Long userId, int page,
                                 CallbackQuery callbackQuery, TelegramClient telegramClient) {
        List<Device> devices = deviceService.getDevicesByUserId(userId);

        if (devices.isEmpty()) {
            sendNoDevicesMessage(callbackQuery, telegramClient);
            return;
        }


        int totalPages = (int) Math.ceil((double) devices.size() / DEVICES_PER_PAGE);

        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }

        int fromIndex = page * DEVICES_PER_PAGE;
        int toIndex = Math.min(fromIndex + DEVICES_PER_PAGE, devices.size());
        List<Device> devicesOnPage = devices.subList(fromIndex, toIndex);


        String text = MessageText.DELETE_DEVICES_MENU.getMessageText()
                .formatted(devices.size());


        List<InlineKeyboardRow> rows = new ArrayList<>();


        for (Device device : devicesOnPage) {
            InlineKeyboardRow row = new InlineKeyboardRow();
            String deviceText = String.format("%s — %s", device.platform(), device.deviceModel());
            row.add(InlineKeyboardButton.builder()
                    .text(deviceText)
                    .callbackData(CallbackName.DELETE_DEVICES.getCallbackName()
                                  + PAYLOAD_SEPARATOR + "hwid"
                                  + PAYLOAD_SEPARATOR + device.hwid()
                                  + PAYLOAD_SEPARATOR + page)
                    .build());
            rows.add(row);
        }


        if (totalPages > 1) {
            InlineKeyboardRow paginationRow = new InlineKeyboardRow();

            if (page > 0) {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.BACK_PAGE.getText())
                        .callbackData(CallbackName.DELETE_DEVICES.getCallbackName()
                                      + PAYLOAD_SEPARATOR + (page - 1))
                        .build());
            } else {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.EMPTY.getText())
                        .callbackData(CallbackName.NONE.getCallbackName())
                        .build());
            }

            paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page + 1, totalPages))
                    .callbackData(CallbackName.NONE.getCallbackName())
                    .build());

            if (page < totalPages - 1) {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.FORWARD_PAGE.getText())
                        .callbackData(CallbackName.DELETE_DEVICES.getCallbackName()
                                      + PAYLOAD_SEPARATOR + (page + 1))
                        .build());
            } else {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.EMPTY.getText())
                        .callbackData(CallbackName.NONE.getCallbackName())
                        .build());
            }

            rows.add(paginationRow);
        }


        InlineKeyboardRow backRow = new InlineKeyboardRow();
        backRow.add(InlineKeyboardButton.builder()
                .text(ButtonText.BACK.getText())
                .callbackData(CallbackName.DEVICES.getCallbackName())
                .build());
        rows.add(backRow);

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(rows)
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

    private void sendNoDevicesMessage(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String text = MessageText.NO_DEVICES_TO_DELETE.getMessageText();

        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder()
                                .text(ButtonText.BACK.getText())
                                .callbackData(CallbackName.DEVICES.getCallbackName())
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
