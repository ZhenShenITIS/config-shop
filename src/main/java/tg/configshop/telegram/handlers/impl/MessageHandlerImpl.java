package tg.configshop.telegram.handlers.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.repositories.RedisStateManager;
import tg.configshop.telegram.config.TelegramConfig;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.constants.DialogStageName;
import tg.configshop.telegram.containers.CommandContainer;
import tg.configshop.telegram.containers.DialogStateContainer;
import tg.configshop.telegram.handlers.MessageHandler;

import java.util.Optional;

@AllArgsConstructor
@Component
public class MessageHandlerImpl implements MessageHandler {

    private final CommandContainer commandContainer;

    private final TelegramConfig telegramConfig;

    private final DialogStateContainer dialogStateContainer;

    private final RedisStateManager<Long, DialogStageName> dialogStageNameRedisStateManager;

    @Override
    public void answerMessage(Message message, TelegramClient telegramClient) {
        Optional<DialogStageName> stage = dialogStageNameRedisStateManager.get(message.getFrom().getId());

        if (stage.isPresent()) {
            dialogStateContainer.retrieveDialogStage(stage.get().getDialogStageName())
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
