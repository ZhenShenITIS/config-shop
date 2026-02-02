package tg.configshop.telegram.methods.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.TelegramMethodName;
import tg.configshop.telegram.methods.TelegramMethodHandler;

@RequiredArgsConstructor
@Component
public class SendPhotoHandler implements TelegramMethodHandler {
    private final TelegramClient telegramClient;
    @Override
    public TelegramMethodName getSupportedMethod() {
        return TelegramMethodName.SEND_PHOTO;
    }

    @Override
    public Class<? extends PartialBotApiMethod> getMethodClass() {
        return SendPhoto.class;
    }

    @Override
    public void handle(Object method) {
        if (method instanceof SendPhoto sendPhoto) {
            telegramClient.executeAsync(sendPhoto);
        }
    }
}
