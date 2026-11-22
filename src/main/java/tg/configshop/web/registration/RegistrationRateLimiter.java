package tg.configshop.web.registration;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

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

    public void login(String address) {
        consume("login:" + address, 30, Duration.ofMinutes(1));
    }

    public void forgotPassword(String address) {
        consume("forgot-password:" + address, 10, Duration.ofHours(1));
    }

    public void resetPassword(String address) {
        consume("reset-password:" + address, 30, Duration.ofMinutes(1));
    }

    public void telegramCode(long telegramId) {
        consume("telegram-code:" + telegramId, 5, Duration.ofMinutes(10));
        consume("telegram-code", 60, Duration.ofMinutes(1));
    }

    public void issueTelegramCode(UUID webUserId) {
        consume("issue-telegram-code:" + webUserId, 10, Duration.ofMinutes(1));
    }

    private void consume(String key, int capacity, Duration period) {
        var bucket = buckets.asMap().computeIfAbsent(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(capacity, period))).build());
        if (!bucket.tryConsume(1)) {
            throw new WebAuthException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_exceeded",
                    "Слишком много запросов. Попробуйте позже.");
        }
    }
}
