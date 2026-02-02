package tg.configshop.controllers;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tg.configshop.services.TelegramRequestHandler;

@RestController
@RequestMapping("/proxy")
@RequiredArgsConstructor
public class MessageController {

    private final TelegramRequestHandler telegramRequestHandler;

    @SneakyThrows
    @PostMapping("/{methodType}")
    public ResponseEntity<String> send(@PathVariable String methodType, @RequestBody String json) {
        telegramRequestHandler.handle(methodType, json);
        return ResponseEntity.ok("OK");
    }

}
