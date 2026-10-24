package tg.configshop.services.impl;

import tg.configshop.repositories.RemnawaveSubscriptionRepository;
import tg.configshop.repositories.AccountRepository;
import tg.configshop.model.RemnawaveSubscription;
import tg.configshop.model.Account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.User;
import org.springframework.web.client.RestClientResponseException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
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
    private final AccountRepository accounts;
    private final RemnawaveSubscriptionRepository subscriptions;
    private final ReferralService referralService;
    private final RemnawaveClient remnawaveClient;
    private final SchedulerService schedulerService;
    private final RemnawaveApiVersion apiVersion;

    @Override
    public boolean isRegistered(Long userId) {
        return botUserRepository.findById(userId).orElse(null) != null;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public BotUser registerUser(User user, Long referrerId) {
        BotUser existing = botUserRepository.findById(user.getId()).orElse(null);
        if (existing != null) return existing;
        String remnawaveUsername = user.getId().toString();
        RemnawaveRegistrationResult remnawaveRegistration = getOrCreateRemnawaveUser(remnawaveUsername, user.getId());
        RemnawaveUserResponse remnaUser = remnawaveRegistration.user();
        if (remnaUser == null || remnaUser.id() == null || remnaUser.id() <= 0) {
            throw new IllegalStateException("Remnawave did not return a positive user ID");
        }
        Account account = accounts.save(Account.builder().build());
        RemnawaveSubscription subscription = subscriptions.save(RemnawaveSubscription.builder()
                .account(account).remnawaveUuid(remnaUser.uuid()).remnawaveId(remnaUser.id())
                .shortId(remnaUser.shortUuid()).build());
        account.getSubscriptions().add(subscription);
        BotUser botUser = BotUser
                .builder()
                .username(user.getUserName())
                .telegramId(user.getId())
                .name(user.getFirstName())
                .lastName(user.getLastName())
                .account(account)
                .build();
        apiVersion.select(botUser.remnawaveRef());
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
