package com.vitc.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Loads a {@code .env} file (KEY=VALUE per line) from the backend working
 * directory - or the project root, one level up - into Spring's
 * {@link ConfigurableEnvironment} before any {@code ${MAIL_HOST:}} style
 * placeholder in {@code application.properties} is resolved.
 *
 * <p><b>Why this exists:</b> {@code .env.example} instructs the developer to
 * copy it to {@code .env} and fill in SMTP / Razorpay values, but plain
 * Spring Boot never reads a {@code .env} file on its own - only real OS
 * environment variables. Without this, {@code MAIL_HOST} etc. silently
 * resolve to an empty string, {@code EmailService.isConfigured()} returns
 * false, and the credential email is skipped with no visible error: the
 * payment still succeeds and the student account is still created, so the
 * only symptom is "the customer never got the email".
 *
 * <p>Real OS/system environment variables (e.g. set on a production server)
 * always take priority over values found in {@code .env} - this only fills
 * in values that are not already set, matching normal dotenv semantics, and
 * it is a no-op (and logs nothing beyond a debug line) when no {@code .env}
 * file exists, e.g. in CI or when the real environment already provides
 * everything.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (Path candidate : new Path[] {
                Path.of(".env"),
                Path.of("backend", ".env"),
                Path.of("..", ".env")
        }) {
            if (Files.isRegularFile(candidate)) {
                Map<String, Object> values = parse(candidate);
                if (!values.isEmpty()) {
                    environment.getPropertySources()
                            .addLast(new MapPropertySource("dotenv:" + candidate, values));
                }
                return; // first match wins
            }
        }
    }

    private Map<String, Object> parse(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).strip();
                String value = line.substring(eq + 1).strip();
                // Strip a single layer of matching quotes: KEY="value" / KEY='value'
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                // .env only fills gaps - a real environment variable already
                // set (e.g. by the OS, Docker, or a hosting provider) wins.
                if (System.getenv(key) == null) {
                    values.put(key, value);
                }
            }
        } catch (IOException ex) {
            // Never fail startup because of an unreadable .env file.
            return new LinkedHashMap<>();
        }
        return values;
    }
}
