package ru.yandex.practicum.mymarket.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.entity.User;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.UserRepository;

import java.util.List;

@Configuration
public class DataInitializer {

  @Bean
  ApplicationListener<ApplicationReadyEvent> seedDatabase(ItemRepository itemRepository) {
    return event -> {
      Mono<Long> countMono = itemRepository.count();
      if (countMono == null) return;
      countMono
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
    };
  }

  @Bean
  ApplicationListener<ApplicationReadyEvent> seedUsers(UserRepository userRepository,
      PasswordEncoder passwordEncoder) {
    return event -> {
      Mono<Long> countMono = userRepository.count();
      if (countMono == null) return;
      countMono
          .filter(count -> count == 0)
          .flatMapMany(count -> userRepository.saveAll(List.of(
              new User("alice", passwordEncoder.encode("alice123")),
              new User("bob",   passwordEncoder.encode("bob123"))
          )))
          .subscribeOn(Schedulers.boundedElastic())
          .blockLast();
    };
  }
}
