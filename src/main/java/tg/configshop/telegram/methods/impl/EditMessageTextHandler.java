package tg.configshop.telegram.methods.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.TelegramMethodName;
import tg.configshop.telegram.methods.TelegramMethodHandler;

@Component
@RequiredArgsConstructor
public class EditMessageTextHandler implements TelegramMethodHandler {
    private final TelegramClient telegramClient;
    @Override
    public TelegramMethodName getSupportedMethod() {
        return TelegramMethodName.EDIT_MESSAGE_TEXT;
    }

    @Override
    public Class<? extends PartialBotApiMethod> getMethodClass() {
        return EditMessageText.class;
    }

    @SneakyThrows
    @Override
    public void handle(Object method) {
        if (method instanceof EditMessageText editMessageText) {
            telegramClient.executeAsync(editMessageText);
        }
    }
}
