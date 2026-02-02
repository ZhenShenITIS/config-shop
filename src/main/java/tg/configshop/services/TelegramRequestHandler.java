package tg.configshop.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.telegram.containers.TelegramMethodContainer;
import tg.configshop.telegram.methods.TelegramMethodHandler;

@Service
@RequiredArgsConstructor
public class TelegramRequestHandler {
    private final TelegramMethodContainer telegramMethodContainer;
    private final TelegramClient telegramClient;
    private final ObjectMapper cleanMapper;

    public void handle (String methodId, String jsonBody) throws JsonProcessingException {
        TelegramMethodHandler handler = telegramMethodContainer.retrieveHandler(methodId);
        if (handler == null) {
            throw new RuntimeException("Couldn't find a suitable handler class by %s methodId".formatted(methodId));
        }
        handler.handle(cleanMapper.readValue(jsonBody, handler.getMethodClass()));
    }
}
