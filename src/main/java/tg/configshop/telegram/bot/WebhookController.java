package tg.configshop.telegram.bot;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;
import tg.configshop.alerting.AlertService;

@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "start.webhook", havingValue = "true")
public class WebhookController extends AbstractBot {
    private final AlertService alertService;

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
                    extractLog(update, e);
                    alertService.alertUnknownException(update, e);

                }
            });

            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            log.atError()
                    .setCause(e)
                    .setMessage("Failed to parse update with Jackson")
                    .addKeyValue("raw_json", json)
                    .log();
            return ResponseEntity.ok("OK");
        }
    }
}
