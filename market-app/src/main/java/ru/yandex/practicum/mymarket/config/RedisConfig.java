package ru.yandex.practicum.mymarket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import ru.yandex.practicum.mymarket.entity.Item;

@Configuration
public class RedisConfig {

    /**
     * Typed template for caching individual {@link Item} objects.
     *
     * Keys   — plain strings (e.g. {@code "item::42"})
     * Values — JSON-serialised {@link Item} via the Spring-managed ObjectMapper,
     *          so the same Jackson configuration (modules, date formats, etc.)
     *          is used everywhere.
     */
    @Bean
    public ReactiveRedisTemplate<String, Item> itemRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<Item> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Item.class);

        RedisSerializationContext<String, Item> context =
                RedisSerializationContext.<String, Item>newSerializationContext(new StringRedisSerializer())
                        .value(valueSerializer)
                        .hashValue(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}
