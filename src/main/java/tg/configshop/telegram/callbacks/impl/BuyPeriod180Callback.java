package tg.configshop.telegram.callbacks.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.callbacks.Callback;

@Component
public class BuyPeriod180Callback extends AbstractBuyPeriodCallback {
    public BuyPeriod180Callback(SubscriptionService subscriptionService) {
        super(subscriptionService);
    }

    public CallbackName getCallback() {
        return CallbackName.BUY_PERIOD_180;
    }
    @Override
    protected int getDaysPeriod() {
        return 180;
    }
}
