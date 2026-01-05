package tg.configshop.telegram.callbacks.impl.subscription;

import org.springframework.stereotype.Component;
import tg.configshop.constants.CallbackName;
import tg.configshop.services.SubscriptionService;

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
