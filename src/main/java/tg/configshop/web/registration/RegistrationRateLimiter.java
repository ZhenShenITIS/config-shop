package tg.configshop.web.registration;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RegistrationRateLimiter {
    private final Cache<String, Bucket> buckets = CacheBuilder.newBuilder()
            .maximumSize(10_000).expireAfterAccess(Duration.ofHours(1)).build();

    public void register(String address) {
        consume("register:" + address, 5, Duration.ofHours(1));
    }

    public void resend(String address) {
        consume("resend:" + address, 10, Duration.ofHours(1));
    }

    public void confirm(String address) {
        consume("confirm:" + address, 30, Duration.ofMinutes(1));
    }

    private void consume(String key, int capacity, Duration period) {
        var bucket = buckets.asMap().computeIfAbsent(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, period))).build());
        if (!bucket.tryConsume(1)) {
            throw new WebRegistrationException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded",
                    "Слишком много запросов. Попробуйте позже.");
        }
    }
}
