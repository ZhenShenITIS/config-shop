package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
}
