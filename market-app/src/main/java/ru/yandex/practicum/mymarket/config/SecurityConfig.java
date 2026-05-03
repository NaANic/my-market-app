package ru.yandex.practicum.mymarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.DelegatingServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.HeaderWriterServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler;
import org.springframework.security.web.server.authentication.logout.WebSessionServerLogoutHandler;
import org.springframework.security.web.server.header.ClearSiteDataServerHttpHeadersWriter;
import ru.yandex.practicum.mymarket.repository.UserRepository;

import java.net.URI;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http
        .authorizeExchange(ex -> ex
            .pathMatchers(HttpMethod.GET, "/", "/items", "/items/*").permitAll()
            .pathMatchers("/login", "/logout", "/css/**", "/images/**",
                "/webjars/**", "/actuator/health").permitAll()
            .anyExchange().authenticated()
        )
        .formLogin(Customizer.withDefaults())
        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutHandler(new DelegatingServerLogoutHandler(
                new WebSessionServerLogoutHandler(),
                new HeaderWriterServerLogoutHandler(
                    new ClearSiteDataServerHttpHeadersWriter(
                        ClearSiteDataServerHttpHeadersWriter.Directive.COOKIES,
                        ClearSiteDataServerHttpHeadersWriter.Directive.STORAGE,
                        ClearSiteDataServerHttpHeadersWriter.Directive.CACHE))
            ))
            .logoutSuccessHandler(new RedirectServerLogoutSuccessHandler() {{
              setLogoutSuccessUrl(URI.create("/items"));
            }})
        )
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public ReactiveUserDetailsService userDetailsService(UserRepository repo) {
    return username -> repo.findByUsername(username)
        .map(u -> org.springframework.security.core.userdetails.User
            .withUsername(u.getUsername())
            .password(u.getPassword())
            .disabled(!u.isEnabled())
            .roles("USER")
            .build());
  }
}
