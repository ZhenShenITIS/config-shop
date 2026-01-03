package tg.configshop.services;

import tg.configshop.model.BotUser;

public interface UserService {
    BotUser getUser (Long userId);
}
