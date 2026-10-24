package tg.configshop.services;

import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.AccountRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.WithdrawalStatus;
import tg.configshop.dto.WithdrawalContext;
import tg.configshop.events.WithdrawalCreatedEvent;
import tg.configshop.model.BotUser;
import tg.configshop.model.Withdrawal;
import tg.configshop.repositories.WithdrawalRepository;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final UserService userService;
    private final BalanceService balanceService;
    private final ReferralService referralService;
    private final AccountRepository accounts;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final BotUserRepository botUsers;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Withdrawal createWithdrawal(long userId, WithdrawalContext context) {
        BotUser user = userService.getUser(userId);
        accounts.findByIdWithLock(user.getAccount().getId()).orElseThrow();
        long amount = context.getAmount();

        if (amount <= 0 || amount > referralService.getAvailableSumToWithdraw(userId)) {
            throw new IllegalStateException("Недостаточно средств для вывода");
        }
        Withdrawal withdrawal = Withdrawal.builder()
                .account(user.getAccount())
                .amount(amount)
                .type(context.getWithdrawalType())
                .requisites(context.getRequisites())
                .build();

        balanceService.withdrawal(withdrawal, false);
        Withdrawal savedWithdrawal = withdrawal;

        eventPublisher.publishEvent(new WithdrawalCreatedEvent(this, savedWithdrawal.getId()));

        return savedWithdrawal;
    }

    @Transactional
    public void approveWithdrawal(String withdrawalId) {
        Withdrawal withdrawal = getWithdrawalOrThrow(withdrawalId);

        if (withdrawal.getStatus() != WithdrawalStatus.IN_PROGRESS) {
            throw new IllegalStateException("Заявка уже обработана");
        }

        withdrawal.setStatus(WithdrawalStatus.DONE);
        withdrawal.setUpdatedAt(java.time.Instant.now());
        withdrawalRepository.save(withdrawal);
    }

    @Transactional
    public void rejectWithdrawal(String withdrawalId) {
        Withdrawal withdrawal = getWithdrawalOrThrow(withdrawalId);

        if (withdrawal.getStatus() != WithdrawalStatus.IN_PROGRESS) {
            throw new IllegalStateException("Заявка уже обработана");
        }

        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawal.setUpdatedAt(java.time.Instant.now());
        withdrawalRepository.save(withdrawal);

        balanceService.withdrawal(withdrawal, true);
    }

    public Withdrawal getWithdrawal(String id) {
        return withdrawalRepository.findById(resolveId(id)).orElseThrow();
    }

    private java.util.UUID resolveId(String value) {
        try { return java.util.UUID.fromString(value); }
        catch (IllegalArgumentException e) {
            return jdbc.queryForObject("select new_id from migration_id_map where entity = 'withdrawals' and old_id = ?", java.util.UUID.class, value);
        }
    }
    public BotUser getRecipient(Withdrawal withdrawal) {
        return botUsers.findFirstByAccount_IdOrderByTelegramIdAsc(withdrawal.getAccount().getId()).orElseThrow();
    }
    private Withdrawal getWithdrawalOrThrow(String id) {
        return withdrawalRepository.findByIdWithLock(resolveId(id))
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found: " + id));
    }
}
