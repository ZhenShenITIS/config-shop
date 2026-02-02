package tg.configshop.telegram.containers;

import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;
import tg.configshop.telegram.methods.TelegramMethodHandler;

import java.util.HashMap;
import java.util.List;

@Component
public class TelegramMethodContainer {

    private final ImmutableMap<String, TelegramMethodHandler> handlers;

    public TelegramMethodContainer(List<TelegramMethodHandler> telegramMethodHandlerList) {
        HashMap<String, TelegramMethodHandler> map = new HashMap<>();
        for (TelegramMethodHandler tmh : telegramMethodHandlerList) {
            map.put(tmh.getSupportedMethod().getName(), tmh);
        }
        handlers = ImmutableMap.copyOf(map);
    }

    public TelegramMethodHandler retrieveHandler(String methodId) {
        return handlers.get(methodId);
    }
}
