package tg.configshop.telegram.handlers.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.repositories.RedisStateManager;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.constants.DialogStageName;
import tg.configshop.telegram.containers.CallbackContainer;
import tg.configshop.telegram.containers.DialogStateContainer;
import tg.configshop.telegram.handlers.CallbackQueryHandler;

import java.util.Optional;

@AllArgsConstructor
@Component
public class CallbackQueryHandlerImpl implements CallbackQueryHandler {
    private final CallbackContainer callbackContainer;

    private final DialogStateContainer dialogStateContainer;

    private final RedisStateManager<Long, DialogStageName> dialogStageNameRedisStateManager;

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {
//        Long userAllowId = Long.parseLong(callbackQuery.getData().split(":")[1]);
        Long userCallbackId = callbackQuery.getFrom().getId();
//        if (!userAllowId.equals(userCallbackId)) {
//            return;
//        }

        Optional<DialogStageName> stage = dialogStageNameRedisStateManager.get(userCallbackId);
        if (stage.isPresent()) {
            dialogStateContainer.retrieveDialogStage(stage.get().getDialogStageName()).processCallbackQuery(callbackQuery, telegramClient);
            return;
        }
        String callbackIdentifier = callbackQuery.getData().split(":")[0];
        callbackContainer.retrieveCallback(callbackIdentifier).processCallback(callbackQuery, telegramClient);
    }
}
