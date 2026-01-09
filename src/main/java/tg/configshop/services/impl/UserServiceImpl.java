package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.UserService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final BotUserRepository botUserRepository;

    @Override
    public BotUser getUser(Long userId) {
        return botUserRepository.findById(userId).orElse(null);
    }

    @Override
    public List<BotUser> getUser(String username) {
        return botUserRepository.findByUsernameIgnoreCase(username);
    }

    @Override
    public void syncRemnawaveUserWithLocalUser(RemnawaveUserResponse userResponse, BotUser botUser) {
        botUser.setExpireAt(userResponse.expireAt());
        botUserRepository.save(botUser);
    }

    @Override
    @Transactional
    public void addToBalance(Long userId, Long amountToAdd) {
        BotUser user = botUserRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setBalance(user.getBalance() + amountToAdd);
        botUserRepository.save(user);
    }
}
