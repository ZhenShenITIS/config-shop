package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.dto.AccountDetails;
import tg.configshop.model.Account;
import tg.configshop.repositories.AccountRepository;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accounts;
    private final BalanceService balanceService;

    @Transactional
    public UUID createAccount() { return accounts.save(Account.builder().build()).getId(); }

    @Transactional(readOnly = true)
    public AccountDetails getAccount(UUID accountId) {
        Account account = requireAccount(accountId);
        return new AccountDetails(accountId, balanceService.balance(accountId), account.getReferralPercentage1lvl(),
                account.getReferralPercentage2lvl(), account.getReferralPercentage3lvl());
    }

    public Account requireAccount(UUID accountId) {
        return accounts.findById(accountId).orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }
}
