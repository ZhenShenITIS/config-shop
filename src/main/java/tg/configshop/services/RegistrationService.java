package tg.configshop.services;

import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import tg.configshop.model.BotUser;

public interface RegistrationService {
    boolean isRegistered (Long userId);
    BotUser registerUser (User user, Long referrerId);

}
