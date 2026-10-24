package tg.configshop.services;

import tg.configshop.repositories.AccountRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.WithdrawalStatus;
import tg.configshop.dto.WithdrawalRequest;
import tg.configshop.dto.WithdrawalDetails;
import tg.configshop.events.WithdrawalCreatedEvent;
import tg.configshop.model.Withdrawal;
import tg.configshop.repositories.WithdrawalRepository;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final WithdrawalRepository withdrawalRepository;
    private final BalanceService balanceService;
    private final ReferralService referralService;
    private final AccountRepository accounts;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public WithdrawalDetails createWithdrawal(java.util.UUID accountId, WithdrawalRequest request) {
        var account = accounts.findByIdWithLock(accountId).orElseThrow();
        long amount = request.amount();
        if (request.type() == null || request.requisites() == null || request.requisites().isBlank())
            throw new IllegalArgumentException("Withdrawal type and requisites are required");

        if (amount < minimumAmount() || amount > referralService.getAvailableSumToWithdraw(accountId)) {
            throw new IllegalStateException("Недостаточно средств для вывода");
        }
        Withdrawal withdrawal = Withdrawal.builder()
                .account(account)
                .amount(amount)
                .type(request.type())
                .requisites(request.requisites())
                .build();

        balanceService.withdrawal(withdrawal, false);
        Withdrawal savedWithdrawal = withdrawal;

        eventPublisher.publishEvent(new WithdrawalCreatedEvent(this, savedWithdrawal.getId()));

        return details(savedWithdrawal);
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

    public long minimumAmount() { return 1000; }
    @Transactional(readOnly = true)
    public WithdrawalDetails getWithdrawal(String id) {
        return details(withdrawalRepository.findById(resolveId(id)).orElseThrow());
    }
    private WithdrawalDetails details(Withdrawal value) {
        return new WithdrawalDetails(value.getId(), value.getAccount().getId(), value.getAmount(), value.getType(),
                value.getStatus(), value.getRequisites(), value.getCreatedAt());
    }

    private java.util.UUID resolveId(String value) {
        try { return java.util.UUID.fromString(value); }
        catch (IllegalArgumentException e) {
            return jdbc.queryForObject("select new_id from migration_id_map where entity = 'withdrawals' and old_id = ?", java.util.UUID.class, value);
        }
    }
    private Withdrawal getWithdrawalOrThrow(String id) {
        return withdrawalRepository.findByIdWithLock(resolveId(id))
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found: " + id));
    }
}
