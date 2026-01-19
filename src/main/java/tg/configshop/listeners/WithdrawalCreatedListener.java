package tg.configshop.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.ButtonText;
import tg.configshop.constants.CallbackName;
import tg.configshop.constants.MessageText;
import tg.configshop.constants.WithdrawalStatus;
import tg.configshop.events.WithdrawalCreatedEvent;
import tg.configshop.model.BotUser;
import tg.configshop.model.Withdrawal;
import tg.configshop.repositories.AdministratorRepository;
import tg.configshop.services.WithdrawalService;
import tg.configshop.util.StringUtil;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawalCreatedListener {

    private final TelegramClient telegramClient;
    private final AdministratorRepository administratorRepository;
    private final WithdrawalService withdrawalService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleWithdrawalCreated(WithdrawalCreatedEvent event) {
        try {
            Withdrawal withdrawal = withdrawalService.getWithdrawal(event.getWithdrawalId());
            BotUser user = withdrawal.getBotUser();
            List<Long> adminIds = administratorRepository.getAdminIdList();

            String text = formatAdminMessage(withdrawal, user);
            InlineKeyboardMarkup markup = createAdminKeyboard(withdrawal.getId());

            for (Long adminId : adminIds) {
                SendMessage msg = SendMessage.builder()
                        .chatId(adminId)
                        .text(text)
                        .parseMode("HTML")
                        .replyMarkup(markup)
                        .build();
                try {
                    telegramClient.execute(msg);
                } catch (Exception e) {
                    log.error("Failed to notify admin {}", adminId, e);
                }
            }
            log.info("Notified {} admins about withdrawal {}", adminIds.size(), withdrawal.getId());

        } catch (Exception e) {
            log.error("Error in WithdrawalCreatedListener", e);
        }
    }

    public static String formatAdminMessage(Withdrawal wd, BotUser user) {
        String statusIcon = switch (wd.getStatus()) {
            case IN_PROGRESS -> "⏳ В ожидании";
            case DONE -> "✅ Выполнено";
            case REJECTED -> "❌ Отклонено";
        };

        String name = StringUtil.getSafeHtmlString(user.getFirstName());
        String type = wd.getType().name(); // Или красивое название из ENUM

        return String.format(MessageText.ADMIN_NEW_WITHDRAWAL.getMessageText(),
                wd.getPublicId(),
                user.getId(), name, user.getId(),
                wd.getAmount(),
                type,
                wd.getRequisites(),
                statusIcon
        );
    }

    public static InlineKeyboardMarkup createAdminKeyboard(Long withdrawalId) {
        String payload = ":" + withdrawalId;
        List<InlineKeyboardRow> rows = new ArrayList<>();

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.ADMIN_APPROVE.getText())
                        .callbackData(CallbackName.ADMIN_APPROVE_WD.getCallbackName() + payload)
                        .build(),
                InlineKeyboardButton.builder()
                        .text(ButtonText.ADMIN_REJECT.getText())
                        .callbackData(CallbackName.ADMIN_REJECT_WD.getCallbackName() + payload)
                        .build()
        ));

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.ADMIN_REFRESH.getText())
                        .callbackData(CallbackName.ADMIN_REFRESH_WD.getCallbackName() + payload)
                        .build()
        ));

        return new InlineKeyboardMarkup(rows);
    }
}
