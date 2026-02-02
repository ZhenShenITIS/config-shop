package tg.configshop.telegram.methods;

import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import tg.configshop.constants.TelegramMethodName;

public interface TelegramMethodHandler {
    TelegramMethodName getSupportedMethod();
    Class<? extends PartialBotApiMethod> getMethodClass();
    void handle(Object method);
}
