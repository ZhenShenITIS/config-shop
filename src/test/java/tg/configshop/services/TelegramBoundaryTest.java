package tg.configshop.services;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TelegramBoundaryTest {
    @Test void sharedServicesEventsAndJobsHaveNoChannelDependencies() throws Exception {
        var root=Path.of("src/main/java/tg/configshop");
        for(String area:new String[]{"services","events","listeners","quartz"}) {
            try(var files=Files.walk(root.resolve(area))) {
                for(var path:files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source=Files.readString(path);
                    for(String forbidden:new String[]{"import org.telegram.","import tg.configshop.telegram.",
                            "import tg.configshop.model.BotUser;","import tg.configshop.repositories.BotUserRepository;","TELEGRAM_BOT_"})
                        assertFalse(source.contains(forbidden),path+" depends on "+forbidden);
                }
            }
        }
    }
}
