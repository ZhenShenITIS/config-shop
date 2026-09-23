package tg.configshop.external_api.remnawave.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tg.configshop.external_api.remnawave.RemnawaveUserRef;

@Component
public class RemnawaveApiVersion {
    private final int version;

    public RemnawaveApiVersion(@Value("${remnawave.api-version:2}") int version) {
        if (version != 2 && version != 3) {
            throw new IllegalArgumentException("Remnawave API version must be 2 or 3");
        }
        this.version = version;
    }

    public boolean isV2() {
        return version == 2;
    }

    public RemnawaveUserRef select(RemnawaveUserRef user) {
        if (user == null) {
            throw new IllegalArgumentException("Remnawave user reference is missing");
        }
        if (isV2()) {
            if (user.uuid() == null || user.uuid().isBlank()) {
                throw new IllegalArgumentException("Remnawave UUID is required for API v2");
            }
            return new RemnawaveUserRef(user.uuid(), null);
        }
        if (user.id() == null || user.id() <= 0) {
            throw new IllegalArgumentException("Positive Remnawave ID is required for API v3");
        }
        return new RemnawaveUserRef(null, user.id());
    }

    public Object identifier(RemnawaveUserRef user) {
        RemnawaveUserRef selected = select(user);
        return isV2() ? selected.uuid() : selected.id();
    }
}
