package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tg.configshop.services.*;
import tg.configshop.repositories.*;
import tg.configshop.model.*;
import tg.configshop.constants.TopUpSource;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import java.util.*;

@Service @RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final BotUserRepository botUserRepository;
    private final BalanceService balanceService;
    @Override @Transactional
    public void addToBalanceAsAdminTopUp(Long userId, Long amount) {
        balanceService.credit(TopUp.builder().account(getUser(userId).getAccount()).value(amount)
            .topUpSource(TopUpSource.ADMIN).build());
    }
    @Override public long getBalance(Long userId) { return balanceService.balance(getUser(userId).getAccount().getId()); }
    @Override public BotUser getUser(Long userId) { return botUserRepository.findById(userId).orElse(null); }
    @Override public List<BotUser> getUser(String username) { return botUserRepository.findByUsernameIgnoreCase(username); }
    @Override public List<BotUser> getAllUsers() { return botUserRepository.findAll(); }
    @Override public void syncRemnawaveUserWithLocalUser(RemnawaveUserResponse response, BotUser user) {
        if (user.getRemnawaveId() != null && !Objects.equals(user.getRemnawaveId(), response.id()))
            throw new IllegalStateException("Conflicting Remnawave ID for user " + user.getId());
    }
}
