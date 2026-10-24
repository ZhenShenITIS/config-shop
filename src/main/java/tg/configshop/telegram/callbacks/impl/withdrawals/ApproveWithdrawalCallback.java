package tg.configshop.telegram.callbacks.impl.withdrawals;

import lombok.RequiredArgsConstructor;
import tg.configshop.telegram.TelegramUserService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
public class ApproveWithdrawalCallback implements Callback {
    private final TelegramUserService userService;

    private final WithdrawalService withdrawalService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.ADMIN_APPROVE_WD;
    }

    @Override
    @AdminOnly
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String[] parts = callbackQuery.getData().split(":");
        String withdrawalId = parts[1];

        try {
            withdrawalService.approveWithdrawal(withdrawalId);

            WithdrawalDetails wd = withdrawalService.getWithdrawal(withdrawalId);
            String text = WithdrawalCreatedListener.formatAdminMessage(wd, userService.getRecipient(wd));

            telegramClient.execute(EditMessageText.builder()
                    .chatId(callbackQuery.getMessage().getChatId())
                    .messageId(callbackQuery.getMessage().getMessageId())
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(WithdrawalCreatedListener.createAdminKeyboard(withdrawalId))
                    .build());

            String userMsg = String.format(MessageText.USER_WITHDRAWAL_APPROVED.getMessageText(),
                    wd.getPublicId(), wd.getAmount());

            if (userService.getRecipient(wd) != null) telegramClient.execute(SendMessage.builder()
                    .chatId(userService.getRecipient(wd).getId())
                    .text(userMsg)
                    .parseMode("HTML")
                    .build());

            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text("✅ Заявка одобрена")
                    .build());

        } catch (IllegalStateException e) {
            answerError(telegramClient, callbackQuery.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void answerError(TelegramClient client, String id) {
        try {
            client.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(id)
                    .text(MessageText.ADMIN_ACTION_ERROR.getMessageText().replaceAll("<[^>]*>", "")) // Alert не поддерживает HTML
                    .showAlert(true)
                    .build());
        } catch (Exception ignored) {}
    }
}
