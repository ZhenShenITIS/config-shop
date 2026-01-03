package tg.configshop.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum MessageText {
    START_TEXT_ACTIVE("""
                Привет, %s
                
                Твоя подписка активна до %s (МСК)
                
                Выбери действие:
                """),
    START_TEXT_EXPIRED("""
            Привет, %s
            
            У тебя на данный момент нет активной подписки
            
            Выбери действие:
            """),
    UNKNOWN_COMMAND("Введена неизвестная команда, повторите ввод");

    private final String messageText;

}
