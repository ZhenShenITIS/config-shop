package tg.configshop.services;

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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Withdrawal createWithdrawal(long userId, WithdrawalContext context) {
        BotUser user = userService.getUser(userId);
        long amount = context.getAmount();

        if (user.getBalance() < amount) {
            throw new IllegalStateException("Недостаточно средств для вывода");
        }

        userService.decreaseBalance(userId, amount);

        Withdrawal withdrawal = Withdrawal.builder()
                .botUser(user)
                .amount(amount)
                .type(context.getWithdrawalType())
                .requisites(context.getRequisites())
                .build();

        Withdrawal savedWithdrawal = withdrawalRepository.save(withdrawal);

        eventPublisher.publishEvent(new WithdrawalCreatedEvent(this, savedWithdrawal.getId()));

        return savedWithdrawal;
    }

    @Transactional
    public void approveWithdrawal(Long withdrawalId) {
        Withdrawal withdrawal = getWithdrawalOrThrow(withdrawalId);

        if (withdrawal.getStatus() != WithdrawalStatus.IN_PROGRESS) {
            throw new IllegalStateException("Заявка уже обработана");
        }

        withdrawal.setStatus(WithdrawalStatus.DONE);
        withdrawalRepository.save(withdrawal);
    }

    @Transactional
    public void rejectWithdrawal(Long withdrawalId) {
        Withdrawal withdrawal = getWithdrawalOrThrow(withdrawalId);

        if (withdrawal.getStatus() != WithdrawalStatus.IN_PROGRESS) {
            throw new IllegalStateException("Заявка уже обработана");
        }

        withdrawal.setStatus(WithdrawalStatus.REJECTED);
        withdrawalRepository.save(withdrawal);

        BotUser user = withdrawal.getBotUser();
        userService.addToBalance(user.getId(), withdrawal.getAmount());
    }

    public Withdrawal getWithdrawal(Long id) {
        return getWithdrawalOrThrow(id);
    }

    private Withdrawal getWithdrawalOrThrow(Long id) {
        return withdrawalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Withdrawal not found: " + id));
    }
}
