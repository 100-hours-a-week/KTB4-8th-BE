package eightjbbm.keepgo.database;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Flyway 배포 설정")
class FlywayConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(MySqlMigrationPreflight.class);

    @Test
    @DisplayName("기본 실행은 baseline 10 설정과 자동 baseline 금지 및 사전 검증을 적용한다")
    void enablesSafeMigrationDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");
            assertThat(environment.getProperty("spring.flyway.baseline-version"))
                    .isEqualTo("10");
            assertThat(environment.getProperty("spring.flyway.baseline-on-migrate"))
                    .isEqualTo("false");
            assertThat(environment.getProperty("spring.flyway.clean-disabled")).isEqualTo("true");
            assertThat(environment.getProperty("spring.flyway.validate-on-migrate"))
                    .isEqualTo("true");
            assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(context).hasSingleBean(MySqlMigrationPreflight.class);
        });
    }

    @Test
    @DisplayName("local과 test 프로필은 Flyway와 사전 검증을 실행하지 않는다")
    void disablesMigrationForLocalAndTest() {
        for (String profile : new String[] {"local", "test"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getEnvironment().getProperty("spring.flyway.enabled"))
                        .isEqualTo("false");
                assertThat(context.getEnvironment().getProperty("spring.flyway.baseline-on-migrate"))
                        .isEqualTo("false");
                assertThat(context).doesNotHaveBean(MySqlMigrationPreflight.class);
            });
        }
    }

    @Test
    @DisplayName("명시적으로 Flyway를 끄면 사전 검증도 실행하지 않는다")
    void disablesPreflightWhenMigrationDisabled() {
        runner.withPropertyValues("spring.flyway.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MySqlMigrationPreflight.class);
        });
    }
}
