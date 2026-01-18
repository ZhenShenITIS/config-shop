package tg.configshop.telegram.callbacks.impl.referrals;

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
import tg.configshop.dto.ReferralWithProfitAndLevel;
import tg.configshop.model.BotUser;
import tg.configshop.services.ReferralService;
import tg.configshop.telegram.callbacks.Callback;
import tg.configshop.util.DateUtil;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RefListCallback implements Callback {

    private final ReferralService referralService;

    private static final String PAYLOAD_SEPARATOR = ":";

    @Override
    public CallbackName getCallback() {
        return CallbackName.REF_LIST;
    }

    @Override
    public void processCallback(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userId = callbackQuery.getFrom().getId();
        String data = callbackQuery.getData();


        int pageNumber = 0;
        if (data.contains(PAYLOAD_SEPARATOR)) {
            try {
                pageNumber = Integer.parseInt(data.split(PAYLOAD_SEPARATOR)[1]);
            } catch (NumberFormatException ignored) {
            }
        }


        Page<ReferralWithProfitAndLevel> page = referralService.getReferralsWithProfitAndLevel(userId, pageNumber);

        String text;
        List<InlineKeyboardRow> rows = new ArrayList<>();


        if (page.isEmpty()) {

            text = MessageText.REFERRAL_LIST_EMPTY.getMessageText();
        } else {

            StringBuilder sb = new StringBuilder();

            sb.append(MessageText.REFERRAL_LIST.getMessageText()).append("\n");

            for (ReferralWithProfitAndLevel item : page.getContent()) {
                BotUser ref = item.botUser();

                String template = DateUtil.isExpired(ref)
                        ? MessageText.REFERRAL_INFO_INACTIVE.getMessageText()
                        : MessageText.REFERRAL_INFO_ACTIVE.getMessageText();

                String dateStr = DateUtil.getPrettyDate(item.referredAt());

                String name = ref.getFirstName();

                sb.append(String.format(template, name, item.lvl(), item.profit(), dateStr)).append("\n\n");
            }
            text = sb.toString();



            if (page.getTotalPages() > 1) {
                InlineKeyboardRow paginationRow = new InlineKeyboardRow();

                if (page.hasPrevious()) {
                    paginationRow.add(InlineKeyboardButton.builder()
                            .text(ButtonText.BACK_PAGE.getText())
                            .callbackData(CallbackName.REF_LIST.getCallbackName() + PAYLOAD_SEPARATOR + (page.getNumber() - 1))
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
                            .callbackData(CallbackName.REF_LIST.getCallbackName() + PAYLOAD_SEPARATOR + (page.getNumber() + 1))
                            .build());
                } else {
                    paginationRow.add(InlineKeyboardButton.builder()
                            .text(ButtonText.EMPTY.getText())
                            .callbackData(CallbackName.NONE.getCallbackName())
                            .build());
                }

                rows.add(paginationRow);
            }

        }

        rows.add(new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(ButtonText.BACK.getText())
                        .callbackData(CallbackName.REFERRAL.getCallbackName())
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
}
