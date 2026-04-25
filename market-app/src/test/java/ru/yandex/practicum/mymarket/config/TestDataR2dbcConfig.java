package ru.yandex.practicum.mymarket.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import io.r2dbc.spi.ConnectionFactory;

/**
 * Test-only configuration that explicitly runs {@code schema.sql} against the
 * H2 R2DBC connection factory.
 *
 * <p>Why this is needed: {@code @DataR2dbcTest} is a slice test that loads only
 * R2DBC-related auto-configuration. It does <em>not</em> honour
 * {@code spring.sql.init.mode} — the {@link org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration}
 * that reads that property is present in the slice's imports list but the
 * reactive R2DBC script-runner it delegates to ({@code R2dbcInitializationConfiguration})
 * requires additional wiring that the slice does not provide.
 *
 * <p>A {@link ConnectionFactoryInitializer} bean is the supported, slice-safe
 * way to run DDL scripts in R2DBC tests.
 *
 * <p>Import this class alongside {@link R2dbcConfig} in every {@code @DataR2dbcTest}:
 * <pre>{@code
 * @DataR2dbcTest
 * @Import({R2dbcConfig.class, TestDataR2dbcConfig.class})
 * @ActiveProfiles("test")
 * class MyRepositoryTest { ... }
 * }</pre>
 */
@TestConfiguration
public class TestDataR2dbcConfig {

  /**
   * Runs {@code src/test/resources/schema.sql} (the H2-compatible DDL) once
   * when the connection factory is first used, creating all tables if they
   * do not already exist ({@code IF NOT EXISTS} guards in the SQL prevent
   * errors when the same in-memory DB is reused across tests).
   */
  @Bean
  public ConnectionFactoryInitializer testSchemaInitializer(ConnectionFactory connectionFactory) {
    ConnectionFactoryInitializer initializer = new ConnectionFactoryInitializer();
    initializer.setConnectionFactory(connectionFactory);
    initializer.setDatabasePopulator(
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));
    return initializer;
  }
}
