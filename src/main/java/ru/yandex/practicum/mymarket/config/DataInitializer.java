package ru.yandex.practicum.mymarket.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;

/**
 * Seeds the database on startup.
 *
 * <p>We use {@link ApplicationReadyEvent} + {@code subscribeOn(boundedElastic)} instead of
 * {@code CommandLineRunner} because:
 * <ul>
 *   <li>{@code CommandLineRunner.run()} is called from the main bootstrap thread while the
 *       Netty event loop is already running. Calling {@code blockLast()} there can trip
 *       BlockHound and risks starving the event loop.</li>
 *   <li>{@link ApplicationReadyEvent} fires after the server is fully started.
 *       {@code subscribeOn(Schedulers.boundedElastic())} moves the subscription onto a
 *       thread pool explicitly designed for blocking I/O, making the {@code blockLast()}
 *       call safe.</li>
 * </ul>
 */
@Configuration
public class DataInitializer {

  @Bean
  ApplicationListener<ApplicationReadyEvent> seedDatabase(ItemRepository itemRepository) {
    return event ->
        itemRepository.count()
            .filter(count -> count == 0)
            .flatMapMany(count -> itemRepository.saveAll(List.of(
            new Item("Футбольный мяч", "Профессиональный мяч для футбола, размер 5", "/images/ball.jpg", 2500),
            new Item("Кроссовки беговые", "Лёгкие кроссовки для бега по асфальту", "/images/sneakers.jpg", 7900),
            new Item("Рюкзак туристический", "Рюкзак 40 л с каркасом для походов", "/images/backpack.jpg", 5400),
            new Item("Бутылка для воды", "Спортивная бутылка 750 мл, BPA-free", "/images/bottle.jpg", 890),
            new Item("Гантели 5 кг", "Пара гантелей с неопреновым покрытием", "/images/dumbbells.jpg", 3200),
            new Item("Коврик для йоги", "Нескользящий коврик 183×61 см, толщина 6 мм", "/images/yoga-mat.jpg", 1500),
            new Item("Скакалка скоростная", "Скакалка с подшипниками, регулируемая длина", "/images/rope.jpg", 650),
            new Item("Фитнес-браслет", "Трекер активности с пульсометром и шагомером", "/images/tracker.jpg", 4300),
            new Item("Теннисная ракетка", "Ракетка для большого тенниса, вес 290 г", "/images/racket.jpg", 6100),
            new Item("Шапка спортивная", "Тёплая шапка для бега зимой, флис", "/images/hat.jpg", 1200),
            new Item("Велосипедный шлем", "Шлем с вентиляцией, размер L", "/images/helmet.jpg", 3800),
            new Item("Перчатки тренировочные", "Перчатки для тренажёрного зала с поддержкой запястья", "/images/gloves.jpg", 1100)
        )))
            .subscribeOn(Schedulers.boundedElastic())
            .blockLast();
  }
}
