package tg.configshop.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "bot_users")
public class BotUser {
    @Id
    private Long id;
    @Column(name = "remnawave_uuid")
    private String remnawaveUuid;
    private String firstName;
    private String lastName;
    private String username;
    @Column(unique = true)
    private String shortId;
    @Column(nullable = false)
    @Builder.Default
    private Long balance = 0L;
    @Builder.Default
    private Integer referralPercentage = 32;
    private Instant expireAt;
    @Builder.Default
    private Instant createdAt = Instant.now();

}
