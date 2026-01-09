package tg.configshop.services;

import tg.configshop.model.BotUser;

import java.util.List;

public interface UserService {
    BotUser getUser (Long userId);
    List<BotUser> getUser (String username);
    void addToBalance(Long userId, Long amountToAdd);
}
