package tg.configshop.telegram.handlers.impl;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.telegram.constants.DialogStageName;
import tg.configshop.telegram.containers.CallbackContainer;
import tg.configshop.telegram.containers.DialogStateContainer;
import tg.configshop.telegram.handlers.CallbackQueryHandler;

@AllArgsConstructor
@Component
public class CallbackQueryHandlerImpl implements CallbackQueryHandler {
    private final CallbackContainer callbackContainer;

    private final UserStateRepository userStateRepository;

    private final DialogStateContainer dialogStateContainer;

    @Override
    public void processCallbackQuery(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        Long userAllowId = Long.parseLong(callbackQuery.getData().split(":")[1]);
        Long userCallbackId = callbackQuery.getFrom().getId();
        if (userAllowId.equals(userCallbackId)) {
            DialogStageName stage = userStateRepository.get(userCallbackId);
            if (!stage.equals(DialogStageName.NONE)) {
                dialogStateContainer.retrieveDialogStage(stage.getDialogStageName()).processCallbackQuery(callbackQuery, telegramClient);
                return;
            }
            String callbackIdentifier = callbackQuery.getData().split(":")[0];
            callbackContainer.retrieveCallback(callbackIdentifier).processCallback(callbackQuery, telegramClient);
        }
    }
}
