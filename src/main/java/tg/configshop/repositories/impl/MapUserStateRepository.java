package tg.configshop.repositories.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import tg.configshop.dto.PaymentContext;
import tg.configshop.repositories.UserStateRepository;
import tg.configshop.constants.DialogStageName;
import tg.configshop.telegram.dialogstages.impl.NoneDialogStage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
@Repository
public class MapUserStateRepository implements UserStateRepository {
    private final Map<Long, DialogStageName> userStateMap = new ConcurrentHashMap<>();
    private final Map<Long, PaymentContext> paymentContextMap = new ConcurrentHashMap<>();
    private final NoneDialogStage noneDialogStage;

    @Override
    public DialogStageName get(Long userId) {
        return userStateMap.getOrDefault(userId, noneDialogStage.getDialogStage());
    }

    @Override
    public void put(Long userId, DialogStageName dialogStageName) {
        userStateMap.put(userId, dialogStageName);
    }

    @Override
    public PaymentContext getPaymentContext(Long userId) {
        return paymentContextMap.get(userId);
    }

    @Override
    public void putPaymentContext(Long userId, PaymentContext paymentContext) {
        paymentContextMap.put(userId, paymentContext);
    }
}
