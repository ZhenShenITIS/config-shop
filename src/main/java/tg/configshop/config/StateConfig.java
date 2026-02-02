package tg.configshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import tg.configshop.constants.DialogStageName;
import tg.configshop.dto.PaymentContext;
import tg.configshop.repositories.RedisStateManager;


import java.time.Duration;

@Configuration
public class StateConfig {

    @Bean
    public RedisStateManager<Long, DialogStageName> dialogStageNameRedisStateManager (RedisTemplate<String, Object> redisTemplate) {
        return RedisStateManager
                .forValueType(DialogStageName.class)
                .longKey()
                .namespace("user_state")
                .ttl(Duration.ofDays(1))
                .build(redisTemplate);
    }

    @Bean
    public RedisStateManager<Long, PaymentContext> paymentContextRedisStateManager (RedisTemplate<String, Object> redisTemplate) {
        return RedisStateManager
                .forValueType(PaymentContext.class)
                .longKey()
                .namespace("payment_context")
                .ttl(Duration.ofDays(1))
                .build(redisTemplate);
    }


}
