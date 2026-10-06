package com.vitc.config;

import com.vitc.entity.Admin;
import com.vitc.entity.enums.AdminRole;
import com.vitc.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the default super admin on first start so the admin panel can be opened.
 * Credentials are configurable through app.admin.default-* properties.
 *
 * <p>The check is idempotent on BOTH the username and the email column, because
 * {@code admins} has a unique constraint on each of them. Checking only the
 * username used to crash startup with
 * {@code Duplicate entry 'admin@vitc.in' for key 'admins.uq_admins_email'}
 * whenever an admin row already existed with that email under a different
 * username. As a last line of defence a constraint violation is logged and
 * swallowed so that seeding can never take the whole application down.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminSeeder {

    @Bean
    ApplicationRunner seedDefaultAdmin(AdminRepository repository,
                                       PasswordEncoder passwordEncoder,
                                       org.springframework.core.env.Environment env) {
        return args -> {
            String username = env.getProperty("app.admin.default-username", "admin");
            String email = env.getProperty("app.admin.default-email", "admin@vitc.in");
            String password = env.getProperty("app.admin.default-password", "Admin@123");

            if (repository.existsByUsernameIgnoreCase(username)) {
                log.debug("Default admin '{}' already exists - skipping seed.", username);
                return;
            }
            if (repository.existsByEmailIgnoreCase(email)) {
                log.info("An admin with email {} already exists - skipping default admin seed.", email);
                return;
            }

            Admin admin = new Admin();
            admin.setUsername(username);
            admin.setEmail(email);
            admin.setFullName(env.getProperty("app.admin.default-name", "Super Admin"));
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole(AdminRole.SUPER_ADMIN);
            admin.setActive(true);

            try {
                repository.save(admin);
                // PART 2A/7 - never log the plain-text password, even on first-run seeding.
                // Whoever configured app.admin.default-password (or the default itself) already
                // knows the value; printing it to the console/log file just gives it a second,
                // less controlled home.
                log.info("Default admin created -> username: {}", username);
            } catch (DataIntegrityViolationException ex) {
                // Another instance seeded concurrently, or a conflicting row exists.
                log.warn("Default admin was not created (a conflicting admin row already exists): {}",
                        ex.getMostSpecificCause().getMessage());
            }
        };
    }
}
