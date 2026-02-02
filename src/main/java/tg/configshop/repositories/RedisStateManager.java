package tg.configshop.repositories;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;


public class RedisStateManager<K, V> {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String namespace;
    private final Class<V> valueType;
    private final Duration ttl;
    private final Function<K, String> keyToStringMapper;


    public RedisStateManager(RedisTemplate<String, Object> redisTemplate, String namespace, Class<V> valueType, Duration ttl, Function<K, String> keyToStringMapper) {
        this.redisTemplate = redisTemplate;
        this.namespace = namespace;
        this.valueType = valueType;
        this.ttl = ttl;
        this.keyToStringMapper = keyToStringMapper;
    }

    public static <V> BuilderStep1<V> forValueType(Class<V> valueType) {
        return new BuilderStep1<>(valueType);
    }

    public void put(K key, V value) {
        String redisKey = buildKey(key);
        redisTemplate.opsForValue().set(redisKey, value, ttl);
    }

    public Optional<V> get(K key) {
        String redisKey = buildKey(key);
        Object value = redisTemplate.opsForValue().get(redisKey);
        return Optional.ofNullable(value).map(valueType::cast);
    }

    public void delete(K key) {
        String redisKey = buildKey(key);
        redisTemplate.delete(redisKey);
    }

    private String buildKey(K key) {
        return namespace + ":" + keyToStringMapper.apply(key);
    }

    public static class BuilderStep1<V> {
        private final Class<V> valueType;

        public BuilderStep1(Class<V> valueType) {
            this.valueType = valueType;
        }

        public <K> BuilderStep2<K, V> keyType(Class<K> keyType, Function<K, String> keyToStringMapper) {
            return new BuilderStep2<>(valueType, keyToStringMapper);
        }

        public BuilderStep2<Long, V> longKey() {
            return new BuilderStep2<>(valueType, Object::toString);
        }

    }


    public static class BuilderStep2<K, V> {
        private final Class<V> valueType;
        private final Function<K, String> keyToStringMapper;

        public BuilderStep2(Class<V> valueType, Function<K, String> keyToStringMapper) {
            this.valueType = valueType;
            this.keyToStringMapper = keyToStringMapper;
        }

        public BuilderStep3<K, V> namespace(String namespace) {
            return new BuilderStep3<>(valueType, keyToStringMapper, namespace);
        }
    }

    public static class BuilderStep3<K, V> {
        private final Class<V> valueType;
        private final Function<K, String> keyToStringMapper;
        private final String namespace;

        public BuilderStep3(Class<V> valueType, Function<K, String> keyToStringMapper, String namespace) {
            this.valueType = valueType;
            this.keyToStringMapper = keyToStringMapper;
            this.namespace = namespace;
        }

        private Duration ttl = Duration.ofMinutes(30);

        public BuilderStep3<K, V> ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public RedisStateManager<K, V> build(RedisTemplate<String, Object> template) {
            return new RedisStateManager<>(template, namespace, valueType, ttl, keyToStringMapper);
        }
    }
}
