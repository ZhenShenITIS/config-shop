package tg.configshop.telegram.callbacks.impl.withdrawals;

import lombok.RequiredArgsConstructor;
import tg.configshop.telegram.TelegramUserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminOnly;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.dto.WithdrawalDetails;
import tg.configshop.services.WithdrawalService;
import tg.configshop.telegram.WithdrawalCreatedListener;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class RefreshWithdrawalCallback implements Callback {
    private final TelegramUserService userService;

    private final WithdrawalService withdrawalService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.ADMIN_REFRESH_WD;
    }

    @Override
    @AdminOnly
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String[] parts = callbackQuery.getData().split(":");
        String withdrawalId = parts[1];

        try {
            WithdrawalDetails wd = withdrawalService.getWithdrawal(withdrawalId);
            String text = WithdrawalCreatedListener.formatAdminMessage(wd, userService.getRecipient(wd));

            try {
                telegramClient.execute(EditMessageText.builder()
                        .chatId(callbackQuery.getMessage().getChatId())
                        .messageId(callbackQuery.getMessage().getMessageId())
                        .text(text)
                        .parseMode("HTML")
                        .replyMarkup(WithdrawalCreatedListener.createAdminKeyboard(withdrawalId))
                        .build());
            } catch (Exception e) {
            }

            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text(MessageText.ADMIN_STATUS_UPDATED.getMessageText())
                    .build());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
