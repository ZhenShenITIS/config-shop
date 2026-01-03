package tg.configshop.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import tg.configshop.model.Referral;
import tg.configshop.repositories.BotUserRepository;
import tg.configshop.repositories.ReferralRepository;
import tg.configshop.services.ReferralService;

@Service
@RequiredArgsConstructor
public class ReferralServiceImpl implements ReferralService {
    private final ReferralRepository referralRepository;
    private final BotUserRepository botUserRepository;

    @Override
    public void createReferral(Long referrerId, Long referralId) {
        // TODO Catching violations of the unique-constrain
        referralRepository.save(Referral
                .builder()
                .referrer(botUserRepository.getReferenceById(referrerId))
                .referral(botUserRepository.getReferenceById(referralId))
                .build());
    }
}
