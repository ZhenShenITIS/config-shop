package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum TelegramMethodName {
    EDIT_MESSAGE_TEXT("editMessageText"),
    SEND_PHOTO("sendPhoto"),
    ANSWER_CALLBACK_QUERY("answerCallbackQuery"),
    SEND_MESSAGE("sendMessage");

    private final String name;
}
