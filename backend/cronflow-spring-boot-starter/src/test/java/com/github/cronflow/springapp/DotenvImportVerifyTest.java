package com.github.cronflow.springapp;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Verifies the backend/.env mechanism: spring.config.import reads a KEY=value file as properties, so
 *  ${CRONFLOW_...} placeholders resolve from it (incl. a URL value containing '&', unquoted). */
class DotenvImportVerifyTest {

    @SpringBootConfiguration
    static class Cfg {}

    @Test
    void envFileIsImportedAsProperties() throws Exception {
        Path env = Files.createTempFile("cronflow", ".env");
        Files.writeString(env, """
                CRONFLOW_AI_ENABLED=true
                CRONFLOW_AI_OLLAMA_URL=http://192.168.0.63:21434
                CRONFLOW_DB_URL=jdbc:mysql://h:3306/cronflow?useSSL=false&serverTimezone=UTC
                """);
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Cfg.class)
                .web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF)
                .properties("spring.config.import=optional:file:" + env + "[.properties]").run()) {
            Environment e = ctx.getEnvironment();
            assertThat(e.getProperty("CRONFLOW_AI_ENABLED")).isEqualTo("true");
            assertThat(e.getProperty("CRONFLOW_AI_OLLAMA_URL")).isEqualTo("http://192.168.0.63:21434");
            // the '&' in a URL survives unquoted (properties value, not shell) — the reason we use
            // spring.config.import and not a shell `source` of the .env.
            assertThat(e.getProperty("CRONFLOW_DB_URL"))
                    .isEqualTo("jdbc:mysql://h:3306/cronflow?useSSL=false&serverTimezone=UTC");
        } finally {
            Files.deleteIfExists(env);
        }
    }
}
