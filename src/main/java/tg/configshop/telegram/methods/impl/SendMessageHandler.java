package tg.configshop.telegram.methods.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.TelegramMethodName;
import tg.configshop.telegram.methods.TelegramMethodHandler;

@Component
@RequiredArgsConstructor
public class SendMessageHandler implements TelegramMethodHandler {
    private final TelegramClient telegramClient;

    @Override
    public TelegramMethodName getSupportedMethod() {
        return TelegramMethodName.SEND_MESSAGE;
    }

    @Override
    public Class<? extends PartialBotApiMethod> getMethodClass() {
        return SendMessage.class;
    }

    @SneakyThrows
    @Override
    public void handle(Object method) {
        if (method instanceof SendMessage sendMessage) {
            telegramClient.executeAsync(sendMessage);
        }
    }
}
