package tg.configshop.telegram.handlers.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.config.TelegramConfig;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.constants.DialogStageName;
import tg.configshop.telegram.containers.CommandContainer;
import tg.configshop.telegram.containers.DialogStateContainer;
import tg.configshop.telegram.handlers.MessageHandler;

@AllArgsConstructor
@Component
public class MessageHandlerImpl implements MessageHandler {

    private final CommandContainer commandContainer;

    private final TelegramConfig telegramConfig;

    private final UserStateRepository userStateRepository;

    private final DialogStateContainer dialogStateContainer;

    @Override
    public void answerMessage(Message message, TelegramClient telegramClient) {
        DialogStageName stage = userStateRepository.get(message.getFrom().getId());

        if (!stage.equals(DialogStageName.NONE)) {
            dialogStateContainer.retrieveDialogStage(stage.getDialogStageName())
                    .answerMessage(message, telegramClient);
        } else {
            boolean hasText = message.hasText();
            boolean hasCaption = message.hasCaption();

            if (hasText || hasCaption) {
                String text = hasText ? message.getText() : message.getCaption();

                if (text.startsWith("/")) {
                    String commandIdentifier = text.split(" ")[0]
                            .split("\n")[0]
                            .split(telegramConfig.getBotName())[0]
                            .toLowerCase();

                    commandContainer.retrieveCommand(commandIdentifier)
                            .handleCommand(message, telegramClient);
                }
            }
        }
    }
}
