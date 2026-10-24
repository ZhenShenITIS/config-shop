package tg.configshop;

import tg.configshop.model.*;
import java.util.*;
public final class TestUsers {
    public static BotUser user(long telegramId, String uuid, Long remoteId) {
        Account account = Account.builder().id(UUID.randomUUID()).build();
        RemnawaveSubscription sub = RemnawaveSubscription.builder().id(UUID.randomUUID())
                .account(account).remnawaveUuid(uuid).remnawaveId(remoteId).build();
        account.getSubscriptions().add(sub);
        return BotUser.builder().telegramId(telegramId).account(account).build();
    }
}
