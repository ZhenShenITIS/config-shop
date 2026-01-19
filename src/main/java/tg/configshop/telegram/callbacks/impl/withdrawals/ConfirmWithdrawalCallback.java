package tg.configshop.telegram.callbacks.impl.withdrawals;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.DialogStageName;
import tg.configshop.constants.MessageText;
import tg.configshop.dto.WithdrawalContext;
import tg.configshop.model.Withdrawal;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.services.WithdrawalService;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.telegram.dto.BotMessageParams;
import tg.configshop.telegram.message_body.StartCommandBody;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConfirmWithdrawalCallback implements Callback {

    private final UserStateRepository userStateRepository;
    private final WithdrawalService withdrawalService;
    private final StartCommandBody startCommandBody;

    @Override
    public CallbackName getCallback() {
        return CallbackName.CONFIRM_WITHDRAWAL;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long userId = callbackQuery.getFrom().getId();
        long chatId = callbackQuery.getMessage().getChatId();
        int messageId = callbackQuery.getMessage().getMessageId();

        WithdrawalContext context = userStateRepository.getWithdrawalContext(userId);

        if (context == null) {
            log.warn("Withdrawal context is null for user {}", userId);
            sendErrorAndMenu(telegramClient, callbackQuery.getFrom(), chatId);
            return;
        }

        try {
            Withdrawal withdrawal = withdrawalService.createWithdrawal(userId, context);

            telegramClient.execute(DeleteMessage.builder()
                    .chatId(chatId)
                    .messageId(messageId)
                    .build());

            String requestText = String.format(MessageText.WITHDRAWAL_CREATED.getMessageText(),
                    withdrawal.getPublicId(),
                    withdrawal.getAmount());

            telegramClient.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(requestText)
                    .parseMode("HTML")
                    .build());

            sendMainMenu(telegramClient, callbackQuery.getFrom(), chatId);
            userStateRepository.put(userId, DialogStageName.NONE);
            userStateRepository.clearWithdrawalContext(userId);

        } catch (Exception e) {
            log.error("Error processing withdrawal confirmation", e);
            sendErrorAndMenu(telegramClient, callbackQuery.getFrom(), chatId);
        }
    }

    private void sendMainMenu(TelegramClient client, User user, long chatId) {
        try {
            BotMessageParams params = startCommandBody.getMessage(user, null);

            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(params.text())
                    .replyMarkup(params.inlineKeyboard())
                    .parseMode("HTML")
                    .build());
        } catch (Exception e) {
            log.error("Error sending main menu", e);
        }
    }

    private void sendErrorAndMenu(TelegramClient client, User user, long chatId) {
        try {
            client.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(MessageText.UNKNOWN_ERROR.getMessageText())
                    .parseMode("HTML")
                    .build());
            sendMainMenu(client, user, chatId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
