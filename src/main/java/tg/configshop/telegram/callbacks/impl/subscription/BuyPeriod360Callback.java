package tg.configshop.telegram.callbacks.impl.subscription;

import org.springframework.stereotype.Component;
import tg.configshop.constants.CallbackName;
import tg.configshop.services.SubscriptionService;

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
