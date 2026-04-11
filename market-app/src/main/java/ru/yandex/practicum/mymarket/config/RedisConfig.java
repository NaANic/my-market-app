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

/**
 * Registers typed {@link ReactiveRedisTemplate} beans for the item cache.
 *
 * <p><b>Naming convention:</b> Spring Boot's {@code RedisAutoConfiguration}
 * registers its own beans named {@code redisTemplate} and
 * {@code stringRedisTemplate}. Using those same names here would cause a
 * {@code BeanDefinitionOverrideException} in Spring Boot 3, which disables
 * bean-definition overriding by default. The beans are therefore named
 * {@code itemCache} and {@code stringCache} — names that do not collide with
 * any auto-configured bean — and {@link ru.yandex.practicum.mymarket.service.ItemService}
 * injects them by those explicit names via {@code @Qualifier}.
 */
@Configuration
public class RedisConfig {

    /**
     * Typed template for caching individual {@link Item} objects.
     * Keys:   plain strings  (e.g. {@code "item::42"})
     * Values: JSON-serialised {@link Item} via the Spring-managed ObjectMapper.
     *
     * <p>Named {@code itemCache} (not {@code itemRedisTemplate}) to avoid
     * colliding with any auto-configured bean of the same name.
     */
    @Bean("itemCache")
    public ReactiveRedisTemplate<String, Item> itemCache(
        ReactiveRedisConnectionFactory factory,
        ObjectMapper objectMapper) {

        Jackson2JsonRedisSerializer<Item> valueSerializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, Item.class);

        RedisSerializationContext<String, Item> context =
            RedisSerializationContext.<String, Item>newSerializationContext(new StringRedisSerializer())
                .value(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    /**
     * General-purpose string template used to cache serialised page results.
     *
     * <p>Named {@code stringCache} (not {@code stringRedisTemplate}) to avoid
     * colliding with the {@code stringRedisTemplate} bean registered by
     * Spring Boot's {@code RedisAutoConfiguration}.
     */
    @Bean("stringCache")
    public ReactiveRedisTemplate<String, String> stringCache(
        ReactiveRedisConnectionFactory factory) {

        return new ReactiveRedisTemplate<>(factory, RedisSerializationContext.string());
    }
}
