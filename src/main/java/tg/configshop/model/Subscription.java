package tg.configshop.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    private Long cost;
    private String name;
    private Integer durationDays;
    private Integer trafficLimitGb;
    private Integer deviceCount;
    private String description;
}
