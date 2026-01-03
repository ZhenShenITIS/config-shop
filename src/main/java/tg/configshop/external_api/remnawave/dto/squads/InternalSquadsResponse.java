package tg.configshop.external_api.remnawave.dto.squads;

import java.util.List;

public record InternalSquadsResponse (
        Integer total,
        List<InternalSquad> internalSquads
) {
}
