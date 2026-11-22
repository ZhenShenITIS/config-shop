package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.dto.AccountOverview;
import tg.configshop.repositories.AccountRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountOverviewService {
    private final AccountRepository accounts;
    private final BalanceService balances;
    private final SubscriptionAccessService subscriptions;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<AccountOverview> load(UUID accountId) {
        return accounts.findById(accountId).map(account -> new AccountOverview(
                balances.balance(accountId), accounts.hasLinkedProfiles(accountId), subscriptions.list(accountId)));
    }
}
