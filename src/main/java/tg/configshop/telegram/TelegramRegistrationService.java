package tg.configshop.telegram;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.telegram.telegrambots.meta.api.objects.User;
import tg.configshop.dto.InitialSubscriptionData;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.services.AccountService;
import tg.configshop.services.ReferralService;
import tg.configshop.services.RegistrationService;
import tg.configshop.services.impl.RegistrationServiceImpl;

@Service @RequiredArgsConstructor
public class TelegramRegistrationService {
    private final BotUserRepository botUserRepository;
    private final AccountService accounts;
    private final RegistrationService registration;
    private final ReferralService referrals;
    private final RemnawaveClient remnawaveClient;

    public boolean isRegistered(Long telegramId) { return botUserRepository.existsById(telegramId); }

    @Transactional
    public BotUser registerUser(User user, Long referrerId) {
        var existing = botUserRepository.findById(user.getId());
        if (existing.isPresent()) return existing.get();
        // Keep the historic external username and metadata for registrations through this channel.
        boolean created = true;
        RemnawaveUserResponse remote;
        try { remote = remnawaveClient.createBasicUser(user.getId().toString(), user.getId()); }
        catch (RestClientResponseException e) {
            if (!RegistrationServiceImpl.usernameAlreadyExists(e)) throw e;
            remote = remnawaveClient.getUserByUsername(user.getId().toString());
            created = false;
        }
        if (remote == null || remote.id() == null || remote.id() <= 0)
            throw new IllegalStateException("Remnawave did not return a positive user ID");
        var accountId = accounts.createAccount();
        registration.registerInitialSubscription(accountId, new InitialSubscriptionData(remote.id(), remote.uuid(),
                remote.shortUuid(), remote.expireAt(), created));
        var profile = botUserRepository.save(BotUser.builder().telegramId(user.getId()).name(user.getFirstName())
                .lastName(user.getLastName()).username(user.getUserName()).account(accounts.requireAccount(accountId)).build());
        if (referrerId != null) {
            var referrer = botUserRepository.findById(referrerId).orElseThrow();
            referrals.createReferral(referrer.getAccount().getId(), accountId);
        }
        referrals.createReferralCode(accountId, "REF_" + user.getId());
        return profile;
    }
}
