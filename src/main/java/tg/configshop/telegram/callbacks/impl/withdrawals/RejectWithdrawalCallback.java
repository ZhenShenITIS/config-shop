package tg.configshop.telegram.callbacks.impl.withdrawals;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.aop.AdminOnly;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.model.Withdrawal;
import tg.configshop.services.WithdrawalService;
import tg.configshop.listeners.WithdrawalCreatedListener;
import tg.configshop.telegram.callbacks.Callback;

@Component
@RequiredArgsConstructor
public class RejectWithdrawalCallback implements Callback {

    private final WithdrawalService withdrawalService;

    @Override
    public CallbackName getCallback() {
        return CallbackName.ADMIN_REJECT_WD;
    }

    @Override
    @AdminOnly
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        String[] parts = callbackQuery.getData().split(":");
        Long withdrawalId = Long.parseLong(parts[1]);

        try {
            withdrawalService.rejectWithdrawal(withdrawalId);

            Withdrawal wd = withdrawalService.getWithdrawal(withdrawalId);
            String text = WithdrawalCreatedListener.formatAdminMessage(wd, wd.getBotUser());

            telegramClient.execute(EditMessageText.builder()
                    .chatId(callbackQuery.getMessage().getChatId())
                    .messageId(callbackQuery.getMessage().getMessageId())
                    .text(text)
                    .parseMode("HTML")
                    .replyMarkup(WithdrawalCreatedListener.createAdminKeyboard(withdrawalId))
                    .build());

            String userMsg = String.format(MessageText.USER_WITHDRAWAL_REJECTED.getMessageText(), wd.getPublicId());

            telegramClient.execute(SendMessage.builder()
                    .chatId(wd.getBotUser().getId())
                    .text(userMsg)
                    .parseMode("HTML")
                    .build());

            telegramClient.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQuery.getId())
                    .text("❌ Заявка отклонена")
                    .build());

        } catch (IllegalStateException e) {
            try {
                telegramClient.execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackQuery.getId())
                        .text("Ошибка: Заявка уже обработана!")
                        .showAlert(true)
                        .build());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
