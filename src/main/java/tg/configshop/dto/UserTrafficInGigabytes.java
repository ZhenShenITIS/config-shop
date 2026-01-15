package tg.configshop.dto;

import tg.configshop.external_api.remnawave.dto.user.UserTraffic;

public record UserTrafficInGigabytes (
        double usedTraffic,
        double lifetimeUsedTraffic
) {
    private static final double BYTE_IN_GIGABYTE = 1_073_741_824.0;


    public UserTrafficInGigabytes(UserTraffic userTraffic) {
        this(
                userTraffic.usedTrafficBytes() / BYTE_IN_GIGABYTE,
                userTraffic.lifetimeUsedTrafficBytes() / BYTE_IN_GIGABYTE
        );
    }


}
