package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.config.R2dbcConfig;
import ru.yandex.practicum.mymarket.config.TestDataR2dbcConfig;
import ru.yandex.practicum.mymarket.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Import({R2dbcConfig.class, TestDataR2dbcConfig.class})
@ActiveProfiles("test")
class UserRepositoryTest {

  @Autowired
  UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll().block();
  }

  @Test
  void save_andFindByUsername_returnsUser() {
    userRepository.save(new User("alice", "$2a$10$hashedpassword")).block();

    StepVerifier.create(userRepository.findByUsername("alice"))
        .assertNext(u -> {
          assertThat(u.getUsername()).isEqualTo("alice");
          assertThat(u.getPassword()).isEqualTo("$2a$10$hashedpassword");
          assertThat(u.isEnabled()).isTrue();
          assertThat(u.getId()).isNotNull();
        })
        .verifyComplete();
  }

  @Test
  void findByUsername_unknownUser_returnsEmpty() {
    StepVerifier.create(userRepository.findByUsername("nobody"))
        .verifyComplete();
  }

  @Test
  void save_duplicateUsername_throwsDataIntegrityViolation() {
    userRepository.save(new User("bob", "hash1")).block();

    StepVerifier.create(userRepository.save(new User("bob", "hash2")))
        .expectError(DataIntegrityViolationException.class)
        .verify();
  }
}
