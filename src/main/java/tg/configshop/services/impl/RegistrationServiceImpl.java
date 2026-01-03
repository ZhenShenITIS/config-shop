package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.ReferralService;
import tg.configshop.services.RegistrationService;
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {
    private final BotUserRepository botUserRepository;
    private final ReferralService referralService;
    private final RemnawaveClient remnawaveClient;

    @Override
    public boolean isRegistered(Long userId) {
        return botUserRepository.findById(userId).orElse(null) != null;
    }

    @Override
    public BotUser registerUser(Message message) {
        User user = message.getFrom();
        Long referrerId = getReferrerId(message);
        RemnawaveUserResponse remnaUser = remnawaveClient.createBasicUser(user.getUserName(), user.getId());
        BotUser botUser = BotUser
                .builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .remnawaveUuid(remnaUser.uuid())
                .shortId(remnaUser.shortUuid())
                .expireAt(remnaUser.expireAt())
                .build();
        botUserRepository.save(botUser);
        if (referrerId != null) {
            referralService.createReferral(referrerId, user.getId());
        }
        return botUser;
    }

    private Long getReferrerId (Message message) {
        String[] parts = message.getText().split(" ");
        if (parts.length > 1) {
            String payload = parts[1];
            try {
                return Long.parseLong(payload);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
