package ru.yandex.practicum.mymarket.service;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.repository.UserRepository;

/**
 * Resolves the currently authenticated user's database ID from the reactive
 * security context.
 *
 * <p>Flow:
 * <ol>
 *   <li>Read the {@code Authentication} principal name from
 *       {@link ReactiveSecurityContextHolder}.</li>
 *   <li>Look up the {@code User} row by username via {@link UserRepository}.</li>
 *   <li>Return the {@code Long id} of that row.</li>
 * </ol>
 *
 * <p>The result is used as the cart/order key in place of the HTTP session ID,
 * so each user's cart and order history survive session rotation and logout.
 */
@Service
public class CurrentUserService {

  private final UserRepository userRepository;

  public CurrentUserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Returns the database ID of the currently authenticated user.
   *
   * @return {@code Mono<Long>} that emits the user ID, or an empty Mono if
   *         there is no authenticated principal (anonymous request)
   */
  public Mono<Long> getCurrentUserId() {
    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .flatMap(auth -> userRepository.findByUsername(auth.getName()))
        .map(user -> user.getId());
  }
}
