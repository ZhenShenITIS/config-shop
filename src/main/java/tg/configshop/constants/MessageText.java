package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MessageText {
    START_TEXT("Привет! Это бот для продажи конфигураций!"),
    UNKNOWN_COMMAND("Введена неизвестная команда, повторите ввод");

    private final String messageText;

}
