package tg.configshop.telegram.callbacks.impl.balance;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.constants.OperationType;
import tg.configshop.dto.OperationView;
import tg.configshop.model.BotUser;
import tg.configshop.services.OperationsService;
import tg.configshop.services.UserService;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Component
public class HistoryCallback implements Callback {
    private final OperationsService operationsService;
    private final UserService userService;

    private static final String PAYLOAD_SEPARATOR = ":";
    
    @Override
    public CallbackName getCallback() {
        return CallbackName.HISTORY;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();
        BotUser user = userService.getUser(userId);


        int pageNumber = 0;
        if (data.contains(PAYLOAD_SEPARATOR)) {
            try {
                pageNumber = Integer.parseInt(data.split(PAYLOAD_SEPARATOR)[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        Page<OperationView> page = operationsService.getOperationsByUserId(userId, pageNumber);
        
        String text;
        List<InlineKeyboardRow> rows = new ArrayList<>();

        if (page.isEmpty()) {
            text = MessageText.HISTORY_EMPTY.getMessageText();
        } else {
            String operationList = getPrettyOperationList(page);
            text = MessageText.HISTORY_HEADER.getMessageText().formatted(user.getBalance(), operationList);
        }


        if (page.getTotalPages() > 1) {
            InlineKeyboardRow paginationRow = new InlineKeyboardRow();

            if (page.hasPrevious()) {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.BACK_PAGE.getText())
                        .callbackData(CallbackName.HISTORY.getCallbackName() + PAYLOAD_SEPARATOR + (page.getNumber() - 1))
                        .build());
            } else {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.EMPTY.getText())
                        .callbackData(CallbackName.NONE.getCallbackName())
                        .build());
            }

            paginationRow.add(InlineKeyboardButton.builder()
                    .text(String.format("%d/%d", page.getNumber() + 1, page.getTotalPages()))
                    .callbackData(CallbackName.NONE.getCallbackName())
                    .build());

            if (page.hasNext()) {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.FORWARD_PAGE.getText())
                        .callbackData(CallbackName.HISTORY.getCallbackName() + PAYLOAD_SEPARATOR + (page.getNumber() + 1))
                        .build());
            } else {
                paginationRow.add(InlineKeyboardButton.builder()
                        .text(ButtonText.EMPTY.getText())
                        .callbackData(CallbackName.NONE.getCallbackName())
                        .build());
            }

            rows.add(paginationRow);
        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.BACK.getText())
                        .callbackData(CallbackName.BALANCE.getCallbackName())
                        .build()
        ));

        EditMessageText editMessage = EditMessageText.builder()
                .chatId(userId)
                .messageId(callbackQuery.getMessage().getMessageId())
                .text(text)
                .parseMode("HTML")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build();

        try {
            telegramClient.execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String getPrettyOperationList(Page<OperationView> page) {
        StringBuilder stringBuilder = new StringBuilder();
        for (OperationView v : page) {;
            String template = switch (v.getOperationType()) {
                case TOP_UP -> MessageText.HISTORY_TOP_UP.getMessageText();
                case PURCHASE -> MessageText.HISTORY_PURCHASE.getMessageText();
                case WITHDRAW -> MessageText.HISTORY_WITHDRAW.getMessageText();
            };
            stringBuilder.append(template.formatted(v.getAmount(), DateUtil.getPrettyDateWithTime(v.getDate()), getDescriptionView(v)));
            stringBuilder.append("\n");
        }
        return stringBuilder.toString();
    }

    private String getDescriptionView(OperationView v) {
        if (OperationType.TOP_UP.equals(v.getOperationType())) {
            return switch (v.getTopUpSource()) {
                case ADMIN -> MessageText.HISTORY_ADMIN.getMessageText();
                case EXTERNAL -> MessageText.HISTORY_EXTERNAL.getMessageText();
                case REFERRAL -> MessageText.HISTORY_REFERRAL.getMessageText();
                case PROMO_CODE -> MessageText.HISTORY_PROMO_CODE.getMessageText();
            };
        } else if (OperationType.PURCHASE.equals(v.getOperationType())){
            return switch (v.getPurchaseType()) {
                case SUBSCRIPTION -> MessageText.HISTORY_SUBSCRIPTION.getMessageText().formatted(v.getDurationDays(), v.getDeviceCount());
                case DEVICE -> MessageText.HISTORY_DEVICE.getMessageText().formatted(v.getDeviceCount());
            };
        } else {
            return switch (v.getWithdrawalStatus()) {
                case DONE -> MessageText.HISTORY_DONE.getMessageText();
                case REJECTED -> MessageText.HISTORY_REJECTED.getMessageText();
                case IN_PROGRESS -> MessageText.HISTORY_IN_PROGRESS.getMessageText();
            };
        }
    }
}
