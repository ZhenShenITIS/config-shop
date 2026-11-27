package tg.configshop.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tg.configshop.constants.SubscriptionChangeStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_changes")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionChange {
    @Id
    private UUID id;
    private UUID subscriptionId;
    private int days;
    private int devices;
    private long price;
    @Enumerated(EnumType.STRING)
    private SubscriptionChangeStatus status;
    private UUID purchaseId;
    private Instant expectedExpiry;
    private Instant previousExpiry;
    private Integer previousDevices;
    private Instant targetExpiry;
    private Integer targetDevices;
    private boolean expiryConfirmed;
    private int attempts;
    private Instant nextAttemptAt;
    private String lastError;

    public SubscriptionChange(UUID id, UUID subscriptionId, int days, int devices, long price) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.days = days;
        this.devices = devices;
        this.price = price;
        this.status = SubscriptionChangeStatus.PREPARING;
        this.nextAttemptAt = Instant.now();
    }

    public boolean isRenewal() {
        return days > 0;
    }
}
