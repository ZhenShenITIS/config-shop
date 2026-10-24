package tg.configshop.model;

import tg.configshop.external_api.remnawave.RemnawaveUserRef;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;


@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "remnawave_subscriptions")
public class RemnawaveSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(optional = false)
    private Account account;
    private String shortId;
    private String remnawaveUuid;
    private Long remnawaveId;
    @Builder.Default
    private Instant createdAt = Instant.now();
    public RemnawaveUserRef remnawaveRef() {
        return new RemnawaveUserRef(remnawaveUuid, remnawaveId);
    }
}
