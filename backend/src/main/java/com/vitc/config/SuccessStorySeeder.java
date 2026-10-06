package com.vitc.config;

import com.vitc.entity.SuccessStory;
import com.vitc.entity.enums.SuccessStoryStatus;
import com.vitc.repository.SuccessStoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * PART 1/6 — SUCCESS STORIES FOUNDATION.
 *
 * Seeds a handful of approved video success stories (students, teachers,
 * parents) the first time the app starts against an empty
 * {@code success_stories} table, so the new public page never has to fall
 * back to hard-coded frontend data while the real Success Stories admin
 * screen (a later part) is being built.
 *
 * <p>Idempotent and additive, same convention as {@link CatalogSeeder} /
 * {@link CourseContentSeeder}: if the table already has rows (real data was
 * added through the API) this seeder does nothing.</p>
 *
 * <p>Videos are short, freely-licensed (Creative Commons) sample clips
 * Google hosts publicly for demo purposes — placeholders only, meant to be
 * replaced with real uploaded testimonial videos through the admin panel.
 * Thumbnails reuse existing reviewer photos already shipped in
 * {@code assets/img/reviewers}.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SuccessStorySeeder {

    @Bean
    @Order(110)
    ApplicationRunner seedSuccessStories(SuccessStoryRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                log.info("Success stories already present — skipping seed.");
                return;
            }

            List<SuccessStory> seed = List.of(
                SuccessStory.builder()
                    .name("Kartikesh Padhi")
                    .category("student")
                    .courseProgram("Advanced Excel & Tally")
                    .designation("VITC Student")
                    .description("On landing his first job after completing Advanced Excel and Tally at VITC.")
                    .videoUrl("https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                    .thumbnailUrl("assets/img/reviewers/kartikesh-padhi.png")
                    .status(SuccessStoryStatus.PUBLISHED)
                    .displayOrder(1)
                    .build(),
                SuccessStory.builder()
                    .name("Neha Gaikwad")
                    .category("student")
                    .courseProgram("Full Stack Development")
                    .designation("VITC Student")
                    .description("How hands-on projects at VITC helped her switch careers into tech.")
                    .videoUrl("https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                    .thumbnailUrl("assets/img/reviewers/neha-gaikwad.png")
                    .status(SuccessStoryStatus.PUBLISHED)
                    .displayOrder(2)
                    .build(),
                SuccessStory.builder()
                    .name("Sparsh Raut")
                    .category("student")
                    .courseProgram("Python & Data Science")
                    .designation("VITC Student")
                    .description("From complete beginner to placed as a junior data analyst.")
                    .videoUrl("https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
                    .thumbnailUrl("assets/img/reviewers/sparsh-raut.png")
                    .status(SuccessStoryStatus.PUBLISHED)
                    .displayOrder(3)
                    .build(),
                SuccessStory.builder()
                    .name("Dattaprasad Sir")
                    .category("teacher")
                    .courseProgram("Java & Full Stack Development")
                    .designation("Senior Trainer, VITC")
                    .description("On what makes VITC's teaching approach different for working professionals.")
                    .videoUrl("https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4")
                    .thumbnailUrl("assets/img/reviewers/nitin-gawand.png")
                    .status(SuccessStoryStatus.PUBLISHED)
                    .displayOrder(1)
                    .build(),
                SuccessStory.builder()
                    .name("Shwetalee Patil")
                    .category("teacher")
                    .courseProgram("Digital Marketing")
                    .designation("Trainer, VITC")
                    .description("Why practical, project-based classes work best for students at VITC.")
                    .videoUrl("https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4")
                    .thumbnailUrl("assets/img/reviewers/shwetalee-patil.png")
                    .status(SuccessStoryStatus.PUBLISHED)
                    .displayOrder(2)
                    .build(),
                SuccessStory.builder()
                    .name("Mrs. Mhatre")
                    .category("parent")
                    .courseProgram("Basic Computer Course")
                    .designation("Parent of a VITC student")
                    .description("On seeing her son grow more confident and skilled after joining VITC.")
                    .videoUrl("https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4")
                    .thumbnailUrl("assets/img/reviewers/rohit-mhatre.png")
                    .status(SuccessStoryStatus.PUBLISHED)
                    .displayOrder(1)
                    .build(),
                SuccessStory.builder()
                    .name("Mr. Bhoir")
                    .category("parent")
                    .courseProgram("Advanced Excel")
                    .designation("Parent of a VITC student")
                    .description("On why he recommends VITC to other parents in Uran.")
                    .videoUrl("https://storage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4")
                    .thumbnailUrl("assets/img/reviewers/sara-bhoir.png")
                    .status(SuccessStoryStatus.PUBLISHED)
                    .displayOrder(2)
                    .build());

            repository.saveAll(seed);
            log.info("Seeded {} success stories.", seed.size());
        };
    }
}
