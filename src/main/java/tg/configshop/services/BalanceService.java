package tg.configshop.services;

import tg.configshop.model.*;
import java.util.UUID;
public interface BalanceService {
    long balance(UUID accountId);
    void adjustAsAdmin(UUID accountId, long amount);
    void credit(TopUp topUp);
    void purchase(Purchase purchase);
    void withdrawal(Withdrawal withdrawal, boolean refund);
}
