package tg.configshop.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.services.*;
import tg.configshop.repositories.*;
import tg.configshop.model.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class TelegramUserService implements LegacyNotificationResolver {
    private final BotUserRepository botUserRepository;
    private final BalanceService balanceService;
    private final SubscriptionAccessService subscriptions;
    @Transactional
    public void addToBalanceAsAdminTopUp(Long userId, Long amount) {
        balanceService.adjustAsAdmin(accountId(userId), amount);
    }
    public long getBalance(Long userId) { return balanceService.balance(accountId(userId)); }
    public BotUser getUser(Long userId) { return botUserRepository.findById(userId).orElse(null); }
    public UUID accountId(Long telegramId) {
        return botUserRepository.findById(telegramId).orElseThrow(() -> new IllegalArgumentException("Telegram profile not found"))
                .getAccount().getId();
    }
    public UUID subscriptionId(Long telegramId) {
        var owned = subscriptions.list(accountId(telegramId));
        if (owned.size() != 1) throw new SubscriptionSelectionException(owned.isEmpty() ? "У аккаунта нет подписки." : "У аккаунта несколько подписок. Это меню поддерживает одну подписку.");
        return owned.getFirst().id();
    }
    public Optional<BotUser> findByAccountId(UUID accountId) {
        return botUserRepository.findFirstByAccount_IdOrderByTelegramIdAsc(accountId);
    }
    public BotUser getRecipient(tg.configshop.dto.WithdrawalDetails withdrawal) {
        return findByAccountId(withdrawal.accountId()).orElse(null);
    }
    public List<Long> recipientIds(UUID accountId) {
        return botUserRepository.findAllByAccount_Id(accountId).stream().map(BotUser::getId).toList();
    }
    public String displayName(UUID accountId) {
        return findByAccountId(accountId).map(BotUser::getFirstName).filter(Objects::nonNull)
                .orElse("Аккаунт " + accountId.toString().substring(0, 8));
    }
    public List<BotUser> getUser(String username) { return botUserRepository.findByUsernameIgnoreCase(username); }
    public List<BotUser> getAllUsers() { return botUserRepository.findAll(); }
}
