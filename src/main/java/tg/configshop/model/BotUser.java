package tg.configshop.model;

import tg.configshop.external_api.remnawave.RemnawaveUserRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "bot_users")
public class BotUser {
    @Id
    @Column(name = "telegram_id")
    private Long telegramId;
    @Column(name = "name")
    private String name;
    private String lastName;
    private String username;
    @ManyToOne(optional = false)
    private Account account;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
    public Long getId() { return telegramId; }
    public String getFirstName() { return name; }

    public RemnawaveSubscription getSubscription() {
        if (account.getSubscriptions().size() != 1) {
            throw new IllegalStateException("Expected one subscription for account " + account.getId());
        }
        return account.getSubscriptions().getFirst();
    }
    public RemnawaveUserRef remnawaveRef() { return getSubscription().remnawaveRef(); }
    public String getRemnawaveUuid() { return getSubscription().getRemnawaveUuid(); }
    public Long getRemnawaveId() { return getSubscription().getRemnawaveId(); }
    public String getShortId() { return getSubscription().getShortId(); }
    public Integer getReferralPercentage1lvl() { return account.getReferralPercentage1lvl(); }
    public Integer getReferralPercentage2lvl() { return account.getReferralPercentage2lvl(); }
    public Integer getReferralPercentage3lvl() { return account.getReferralPercentage3lvl(); }
}
