package tg.configshop.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import tg.configshop.external_api.remnawave.RemnawaveClient;
import tg.configshop.external_api.remnawave.config.RemnawaveApiVersion;
import tg.configshop.external_api.remnawave.dto.user.RemnawaveUserResponse;
import tg.configshop.model.BotUser;
import tg.configshop.repositories.BotUserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemnawaveIdMigrationService {
    private final BotUserRepository botUserRepository;
    private final RemnawaveClient remnawaveClient;
    private final RemnawaveApiVersion apiVersion;

    public Result migrate() {
        if (!apiVersion.isV2()) {
            throw new IllegalStateException("Заполнение ID доступно только в режиме API v2.");
        }
        List<BotUser> users = botUserRepository.findAllByRemnawaveIdIsNullOrderByIdAsc();
        List<String> errors = new ArrayList<>();
        int processed = 0;
        int updated = 0;
        for (BotUser user : users) {
            processed++;
            try {
                apiVersion.select(user.remnawaveRef());
                RemnawaveUserResponse response = remnawaveClient.getUser(user.remnawaveRef());
                if (response == null || response.id() == null || response.id() <= 0
                        || !Objects.equals(user.getRemnawaveUuid(), response.uuid())) {
                    errors.add(user.getId() + ": некорректный ID или несовпадение UUID в ответе панели");
                    continue;
                }
                if (botUserRepository.updateRemnawaveId(user.getId(), response.id()) != 1) {
                    errors.add(user.getId() + ": локальный пользователь не найден при сохранении");
                    continue;
                }
                updated++;
            } catch (Exception e) {
                // Exception messages from HTTP clients can contain entire API responses.
                String reason = e instanceof RestClientResponseException http
                        ? "HTTP " + http.getStatusCode().value() : e.getClass().getSimpleName();
                errors.add(user.getId() + ": " + reason);
                log.warn("Remnawave ID migration failed for local user {}: {}", user.getId(), reason);
                if (e instanceof RestClientResponseException http
                        && (http.getStatusCode().value() == 401 || http.getStatusCode().value() == 403)) {
                    break;
                }
            }
        }
        return new Result(processed, updated, botUserRepository.countByRemnawaveIdIsNull(), List.copyOf(errors));
    }

    public record Result(int processed, int updated, long remaining, List<String> errors) {
        public String report() {
            String summary = "Обработано: " + processed
                    + "\nЗаполнено: " + updated
                    + "\nОшибок: " + errors.size()
                    + "\nОсталось без ID: " + remaining;
            return errors.isEmpty() ? summary : summary + "\n\n" + String.join("\n", errors);
        }
    }
}
