package com.vitc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Course;
import com.vitc.entity.CourseLesson;
import com.vitc.entity.CourseModule;
import com.vitc.repository.CourseLessonRepository;
import com.vitc.repository.CourseModuleRepository;
import com.vitc.repository.CourseRepository;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;

/**
 * Seeds the course curriculum (modules + video lessons) from
 * {@code course-content-seed.json} so the student portal reads lessons from the
 * database instead of hard-coded frontend JavaScript.
 *
 * <p>Idempotent and additive: a course that already has modules is skipped, and
 * nothing existing is ever modified or deleted. Runs after
 * {@link CatalogSeeder} so courses exist first.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CourseContentSeeder {

    @Bean
    @Order(100)
    ApplicationRunner seedCourseContent(CourseRepository courses,
                                        CourseModuleRepository modules,
                                        CourseLessonRepository lessons,
                                        ObjectMapper objectMapper) {
        return args -> {
            ClassPathResource resource = new ClassPathResource("course-content-seed.json");
            if (!resource.exists()) {
                log.info("course-content-seed.json not found - skipping curriculum seeding");
                return;
            }
            JsonNode root;
            try (InputStream in = resource.getInputStream()) {
                root = objectMapper.readTree(in);
            } catch (Exception ex) {
                log.warn("Could not read course-content-seed.json: {}", ex.getMessage());
                return;
            }
            JsonNode byCode = root.path("courses");
            JsonNode fallback = root.path("default");

            int seededCourses = 0;
            for (Course course : courses.findAll()) {
                if (modules.countByCourseId(course.getId()) > 0) {
                    continue; // already has a curriculum - never overwrite
                }
                JsonNode plan = course.getCode() == null ? null : byCode.get(course.getCode().toUpperCase());
                if (plan == null || !plan.isArray() || plan.isEmpty()) {
                    plan = fallback;
                }
                if (plan == null || !plan.isArray() || plan.isEmpty()) {
                    continue;
                }
                int moduleOrder = 1;
                for (JsonNode m : plan) {
                    CourseModule module = modules.save(CourseModule.builder()
                            .course(course)
                            .title(m.path("title").asText("Module " + moduleOrder))
                            .description(m.path("description").asText(null))
                            .displayOrder(moduleOrder++)
                            .active(true)
                            .build());
                    int lessonOrder = 1;
                    for (JsonNode l : m.path("lessons")) {
                        lessons.save(CourseLesson.builder()
                                .module(module)
                                .title(l.path("title").asText("Lesson " + lessonOrder))
                                .description(l.path("description").asText(null))
                                .videoUrl(l.path("videoUrl").asText(null))
                                .duration(l.path("duration").asText(null))
                                .displayOrder(lessonOrder++)
                                .active(true)
                                .build());
                    }
                }
                seededCourses++;
            }
            if (seededCourses > 0) {
                log.info("Seeded curriculum for {} course(s)", seededCourses);
            }
        };
    }
}
