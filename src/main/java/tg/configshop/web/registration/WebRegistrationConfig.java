package tg.configshop.web.registration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class WebRegistrationConfig {
    @Bean
    Clock registrationClock() {
        return Clock.systemUTC();
    }
}
