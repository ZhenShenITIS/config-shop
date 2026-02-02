package tg.configshop.repositories.impl;

import tg.configshop.constants.DialogStageName;
import tg.configshop.dto.PaymentContext;
import tg.configshop.dto.WithdrawalContext;
import tg.configshop.repositories.UserStateRepository;

public class RedisUserStateRepository implements UserStateRepository {
    @Override
    public DialogStageName get(Long userId) {
        return null;
    }

    @Override
    public void put(Long userId, DialogStageName dialogStageName) {

    }

    @Override
    public PaymentContext getPaymentContext(Long userId) {
        return null;
    }

    @Override
    public void putPaymentContext(Long userId, PaymentContext paymentContext) {

    }

    @Override
    public void clearPaymentContext(Long userId) {

    }

    @Override
    public WithdrawalContext getWithdrawalContext(Long userId) {
        return null;
    }

    @Override
    public void putWithdrawalContext(Long userId, WithdrawalContext paymentContext) {

    }

    @Override
    public void clearWithdrawalContext(Long userId) {

    }
}
