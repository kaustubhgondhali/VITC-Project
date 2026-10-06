package com.vitc.config;

import com.vitc.entity.User;
import com.vitc.entity.enums.UserRole;
import com.vitc.entity.enums.UserStatus;
import com.vitc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the default Teacher Admin account on first start so the Teacher
 * panel can be opened. Credentials are configurable through
 * app.teacher.default-* properties, defaulting to VITCteacher / VITC@123.
 *
 * <p>Safety rules (must hold on every application start, forever):</p>
 * <ul>
 *   <li>The account is created ONLY if a user with this username does not
 *       already exist - checked against the {@code username} column, which
 *       is unique. Re-running this seeder is always a no-op once the row
 *       exists.</li>
 *   <li>An existing Teacher account's password and {@code mustChangePassword}
 *       flag are never touched here - only initial creation sets them.</li>
 *   <li>The plain-text password is never logged, returned, or persisted -
 *       only its BCrypt hash is stored.</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TeacherSeeder {

    @Bean
    ApplicationRunner seedDefaultTeacher(UserRepository repository,
                                          PasswordEncoder passwordEncoder,
                                          Environment env) {
        return args -> {
            String username = env.getProperty("app.teacher.default-username", "VITCteacher");
            String email = env.getProperty("app.teacher.default-email", "teacher@vitc.in");
            String password = env.getProperty("app.teacher.default-password", "VITC@123");

            if (repository.existsByUsernameIgnoreCase(username)) {
                log.debug("Default teacher '{}' already exists - skipping seed.", username);
                return;
            }
            if (repository.existsByEmailIgnoreCase(email)) {
                log.info("A user with email {} already exists - skipping default teacher seed.", email);
                return;
            }

            User teacher = new User();
            teacher.setUsername(username);
            teacher.setEmail(email);
            teacher.setFullName(env.getProperty("app.teacher.default-name", "VITC Teacher"));
            teacher.setPasswordHash(passwordEncoder.encode(password));
            teacher.setRole(UserRole.TEACHER);
            teacher.setStatus(UserStatus.ACTIVE);
            teacher.setMustChangePassword(true);

            try {
                repository.save(teacher);
                log.info("Default teacher account created -> username: {}", username);
            } catch (DataIntegrityViolationException ex) {
                // Another instance seeded concurrently, or a conflicting row exists.
                log.warn("Default teacher was not created (a conflicting user row already exists): {}",
                        ex.getMostSpecificCause().getMessage());
            }
        };
    }
}
