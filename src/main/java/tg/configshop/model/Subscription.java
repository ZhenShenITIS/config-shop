package tg.configshop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "subscriptions", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_duration_device",
                columnNames = {"duration_days", "device_count"}
        )
})
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long cost;
    @Column(nullable = false)
    private String name;
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;
    private Integer trafficLimitGb;
    @Column(name = "device_count", nullable = false)
    private Integer deviceCount;
    @Column(columnDefinition = "TEXT")
    private String description;
}
