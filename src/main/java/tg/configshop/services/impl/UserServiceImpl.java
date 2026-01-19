package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.constants.TopUpSource;
import tg.configshop.exceptions.subscription.InsufficientBalanceException;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.model.TopUp;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.TopUpRepository;
import tg.configshop.services.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final BotUserRepository botUserRepository;
    private final TopUpRepository topUpRepository;

    @Transactional
    @Override
    public void addToBalanceAsAdminTopUp(Long userId, Long amountToAdd) {
        addBalance(userId, amountToAdd);
        topUpRepository.save(TopUp
                .builder()
                        .botUser(botUserRepository.getReferenceById(userId))
                        .value(amountToAdd)
                        .topUpSource(TopUpSource.ADMIN)
                .build());

    }

    @Override
    public BotUser getUser(Long userId) {
        return botUserRepository.findById(userId).orElse(null);
    }

    @Override
    public List<BotUser> getUser(String username) {
        return botUserRepository.findByUsernameIgnoreCase(username);
    }

    @Override
    public void decreaseBalance(Long userId, Long amount) throws InsufficientBalanceException {
        BotUser user = botUserRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        if (user.getBalance() < amount) {
            throw new InsufficientBalanceException("Insufficient balance");
        }
        user.setBalance(user.getBalance() - amount);
        botUserRepository.save(user);
    }

    @Override
    public List<BotUser> getAllUsers() {
        return botUserRepository.findAll();
    }

    @Override
    public void syncRemnawaveUserWithLocalUser(RemnawaveUserResponse userResponse, BotUser botUser) {
        botUser.setExpireAt(userResponse.expireAt());
        botUserRepository.save(botUser);
    }

    @Override
    @Transactional
    public void addToBalance(Long userId, Long amountToAdd) {
        addBalance(userId, amountToAdd);
    }

    private void addBalance (Long userId, Long amountToAdd) {
        BotUser user = botUserRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setBalance(user.getBalance() + amountToAdd);
        botUserRepository.save(user);
    }
}
