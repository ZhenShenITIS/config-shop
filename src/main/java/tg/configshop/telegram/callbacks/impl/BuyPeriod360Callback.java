package tg.configshop.telegram.callbacks.impl;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import tg.configshop.constants.CallbackName;
import tg.configshop.services.SubscriptionService;
import tg.configshop.telegram.callbacks.Callback;

@Component
public class BuyPeriod360Callback extends AbstractBuyPeriodCallback {
    public BuyPeriod360Callback(SubscriptionService subscriptionService) {
        super(subscriptionService);
    }

    @Override
    public CallbackName getCallback() {
        return CallbackName.BUY_PERIOD_360;
    }


    @Override
    protected int getDaysPeriod() {
        return 360;
    }
}
