package tg.configshop.telegram.bot;

// Важно: Импортируем Jackson 2 (com.fasterxml...), а не 3 (tools.jackson...)
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "start.webhook", havingValue = "true")
public class WebhookController {

    private final WebhookBot webhookBot;

    private final ObjectMapper jackson2Mapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<String> onUpdateReceived(@RequestBody String json) {
        try {
            Update update = jackson2Mapper.readValue(json, Update.class);

            Thread.startVirtualThread(() -> {
                try {
                    webhookBot.processUpdate(update);
                } catch (Exception e) {
                    log.error("Error processing update", e);
                }
            });

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.error("Failed to parse update with Jackson 2. JSON: {}", json, e);
            return ResponseEntity.ok("OK");
        }
    }
}
