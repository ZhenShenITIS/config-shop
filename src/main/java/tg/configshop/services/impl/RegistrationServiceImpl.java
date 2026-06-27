package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;
import org.springframework.web.client.RestClientResponseException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.quartz.services.SchedulerService;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.ReferralService;
import tg.configshop.services.RegistrationService;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationServiceImpl implements RegistrationService {
    private final BotUserRepository botUserRepository;
    private final ReferralService referralService;
    private final RemnawaveClient remnawaveClient;
    private final SchedulerService schedulerService;

    @Override
    public boolean isRegistered(Long userId) {
        return botUserRepository.findById(userId).orElse(null) != null;
    }

    @Override
    public BotUser registerUser(User user, Long referrerId) {
        String remnawaveUsername = user.getId().toString();
        RemnawaveRegistrationResult remnawaveRegistration = getOrCreateRemnawaveUser(remnawaveUsername, user.getId());
        RemnawaveUserResponse remnaUser = remnawaveRegistration.user();
        BotUser botUser = BotUser
                .builder()
                .username(user.getUserName())
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
        referralService.createReferralCode(user.getId());
        if (remnawaveRegistration.created()) {
            schedulerService.scheduleTrialTrafficChecks(user.getId());
        } else {
            schedulerService.scheduleSubscriptionNotifications(user.getId(), remnaUser.expireAt());
        }
        return botUser;
    }

    private RemnawaveRegistrationResult getOrCreateRemnawaveUser(String username, Long telegramId) {
        try {
            return new RemnawaveRegistrationResult(remnawaveClient.createBasicUser(username, telegramId), true);
        } catch (RestClientResponseException e) {
            if (!isUsernameAlreadyExistsError(e)) {
                throw e;
            }
            log.warn("Remnawave user with username {} already exists, linking existing user", username);
            return new RemnawaveRegistrationResult(remnawaveClient.getUserByUsername(username), false);
        }
    }

    private boolean isUsernameAlreadyExistsError(RestClientResponseException e) {
        String responseBody = e.getResponseBodyAsString();
        return e.getStatusCode().is4xxClientError()
                && (responseBody.contains("\"errorCode\":\"A019\"")
                || responseBody.contains("User username already exists"));
    }

    private record RemnawaveRegistrationResult(
            RemnawaveUserResponse user,
            boolean created
    ) {
    }

}
