package com.vitc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitc.entity.Assignment;
import com.vitc.entity.BlogPost;
import com.vitc.entity.Course;
import com.vitc.entity.Faq;
import com.vitc.entity.GalleryItem;
import com.vitc.entity.PricingPlan;
import com.vitc.entity.Review;
import com.vitc.entity.Testimonial;
import com.vitc.repository.AssignmentRepository;
import com.vitc.repository.BlogPostRepository;
import com.vitc.repository.CourseRepository;
import com.vitc.repository.FaqRepository;
import com.vitc.repository.GalleryItemRepository;
import com.vitc.repository.PricingPlanRepository;
import com.vitc.repository.ReviewRepository;
import com.vitc.repository.TestimonialRepository;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Seeds the catalogue (courses, assignments, reviews, FAQs, testimonials,
 * pricing plans, gallery items and blog posts) from {@code catalog-seed.json} the first
 * time the application starts against an empty database.
 *
 * <p>This replaces the old hard-coded {@code assets/js/data.js} catalogue on
 * the website: every public page now reads this data through the REST API,
 * and the admin panel can edit it. Seeding is idempotent — existing rows are
 * never overwritten and empty-by-choice tables are only filled once.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CatalogSeeder {

    @Bean
    ApplicationRunner seedCatalogue(CourseRepository courses,
                                    AssignmentRepository assignments,
                                    ReviewRepository reviews,
                                    FaqRepository faqs,
                                    TestimonialRepository testimonials,
                                    PricingPlanRepository pricingPlans,
                                    GalleryItemRepository galleryItems,
                                    BlogPostRepository blogPosts,
                                    ObjectMapper objectMapper) {
        return args -> {
            ClassPathResource resource = new ClassPathResource("catalog-seed.json");
            if (!resource.exists()) {
                log.info("catalog-seed.json not found — skipping catalogue seeding");
                return;
            }
            JsonNode root;
            try (InputStream in = resource.getInputStream()) {
                root = objectMapper.readTree(in);
            } catch (Exception ex) {
                log.warn("Unable to read catalog-seed.json: {}", ex.getMessage());
                return;
            }

            int created = 0;
            java.util.concurrent.atomic.AtomicInteger reconciled = new java.util.concurrent.atomic.AtomicInteger();

            for (JsonNode n : array(root, "courses")) {
                String code = text(n, "code");
                if (code == null) {
                    continue;
                }
                if (courses.existsByCode(code)) {
                    /*
                     * PART 2/6 - VITC course-visibility fix.
                     *
                     * This course code was already seeded on a previous
                     * boot, so the loop above (correctly) does not touch it
                     * again - re-running the seeder must never overwrite an
                     * admin's edits or override an admin's deliberate
                     * "Hide" action. But that same idempotency is exactly
                     * why an unrelated bug (e.g. a schema change that
                     * defaulted a NOT NULL "active" column to false for
                     * pre-existing rows - see Course.active) can leave one
                     * of these seed courses permanently invisible with no
                     * seeder run ever able to correct it.
                     *
                     * Reconcile ONLY courses that are BOTH:
                     *   1. one of this project's own known seed courses, and
                     *   2. still exactly as this seeder created them -
                     *      updatedAt == createdAt means nobody has ever
                     *      saved an edit against this row (Course Management
                     *      "Edit"/"Hide"/"Publish" all update updatedAt).
                     * so a course an admin has ever actually opened and
                     * saved - active or not - is left completely alone.
                     * This is a narrow data-integrity correction, not a
                     * "publish everything" switch.
                     */
                    courses.findByCode(code).ifPresent(existing -> {
                        if (Boolean.FALSE.equals(existing.getActive())
                                && existing.getUpdatedAt() != null
                                && existing.getUpdatedAt().equals(existing.getCreatedAt())) {
                            existing.setActive(true);
                            courses.save(existing);
                            reconciled.incrementAndGet();
                            log.warn("Reconciled seed course '{}' back to active=true - it was never edited "
                                    + "since creation, so its active=false could only have come from a schema "
                                    + "default, not an admin action", code);
                        }
                    });
                    continue;
                }
                Course c = new Course();
                c.setCode(code);
                c.setTitle(text(n, "title"));
                c.setDescription(text(n, "description"));
                c.setPrice(decimal(n, "price"));
                c.setMeta(text(n, "meta"));
                c.setLevel(text(n, "level"));
                c.setCategory(text(n, "category"));
                c.setDurationMonths(text(n, "durationMonths"));
                c.setIcon(text(n, "icon"));
                c.setActive(true);
                courses.save(c);
                created++;
            }

            for (JsonNode n : array(root, "assignments")) {
                String code = text(n, "code");
                if (code == null || assignments.existsByCode(code)) {
                    continue;
                }
                Assignment a = new Assignment();
                a.setCode(code);
                a.setTitle(text(n, "title"));
                a.setDescription(text(n, "description"));
                a.setPrice(decimal(n, "price"));
                a.setTech(text(n, "tech"));
                a.setDifficulty(text(n, "difficulty"));
                a.setDeliveryDays(text(n, "deliveryDays"));
                a.setCategory(text(n, "category"));
                a.setIcon(text(n, "icon"));
                a.setFeatures(text(n, "features"));
                a.setActive(true);
                assignments.save(a);
                created++;
            }

            if (reviews.count() == 0) {
                for (JsonNode n : array(root, "reviews")) {
                    Review r = new Review();
                    r.setReviewerName(text(n, "reviewerName"));
                    r.setEmail(text(n, "email"));
                    r.setCourseCode(text(n, "courseCode"));
                    r.setCourseTitle(text(n, "courseTitle"));
                    r.setRating(n.path("rating").asInt(5));
                    r.setComment(text(n, "comment"));
                    r.setImageUrl(text(n, "imageUrl"));
                    r.setApproved(n.path("approved").asBoolean(true));
                    r.setFeatured(n.path("featured").asBoolean(false));
                    reviews.save(r);
                    created++;
                }
            }

            for (JsonNode n : array(root, "faqs")) {
                String question = text(n, "question");
                if (question == null || faqs.existsByQuestionIgnoreCase(question)) {
                    continue;
                }
                Faq f = new Faq();
                f.setQuestion(question);
                f.setAnswer(text(n, "answer"));
                f.setCategory(text(n, "category"));
                f.setDisplayOrder(n.path("displayOrder").asInt(0));
                f.setActive(true);
                faqs.save(f);
                created++;
            }

            if (testimonials.count() == 0) {
                for (JsonNode n : array(root, "testimonials")) {
                    Testimonial t = new Testimonial();
                    t.setName(text(n, "name"));
                    t.setRole(text(n, "role"));
                    t.setPhotoUrl(text(n, "photoUrl"));
                    t.setRating(n.path("rating").asInt(5));
                    t.setMessage(text(n, "message"));
                    t.setApproved(true);
                    testimonials.save(t);
                    created++;
                }
            }

            if (pricingPlans.count() == 0) {
                for (JsonNode n : array(root, "pricingPlans")) {
                    PricingPlan p = new PricingPlan();
                    p.setName(text(n, "name"));
                    p.setTagline(text(n, "tagline"));
                    p.setPrice(decimal(n, "price"));
                    p.setOldPrice(n.hasNonNull("oldPrice") ? decimal(n, "oldPrice") : null);
                    p.setCurrency(text(n, "currency"));
                    p.setBillingPeriod(text(n, "billingPeriod"));
                    p.setFeatures(text(n, "features"));
                    p.setCategory(text(n, "category"));
                    p.setDisplayOrder(n.path("displayOrder").asInt(0));
                    p.setHighlighted(n.path("highlighted").asBoolean(false));
                    p.setActive(true);
                    pricingPlans.save(p);
                    created++;
                }
            }

            if (galleryItems.count() == 0) {
                for (JsonNode n : array(root, "galleryItems")) {
                    GalleryItem g = new GalleryItem();
                    g.setTitle(text(n, "title"));
                    g.setImageUrl(text(n, "imageUrl"));
                    g.setCategory(text(n, "category"));
                    g.setCaption(text(n, "caption"));
                    galleryItems.save(g);
                    created++;
                }
            }

            for (JsonNode n : array(root, "blogPosts")) {
                String slug = text(n, "slug");
                if (slug == null || blogPosts.existsBySlug(slug)) {
                    continue;
                }
                BlogPost b = new BlogPost();
                b.setSlug(slug);
                b.setTitle(text(n, "title"));
                b.setExcerpt(text(n, "excerpt"));
                b.setContent(text(n, "content"));
                b.setCoverImageUrl(text(n, "coverImageUrl"));
                b.setAuthor(text(n, "author"));
                b.setTags(text(n, "tags"));
                b.setPublished(n.path("published").asBoolean(true));
                String publishedAt = text(n, "publishedAt");
                b.setPublishedAt(publishedAt != null ? LocalDateTime.parse(publishedAt) : LocalDateTime.now());
                blogPosts.save(b);
                created++;
            }

            if (created > 0) {
                log.info("Catalogue seeding complete — {} record(s) inserted", created);
            }
            if (reconciled.get() > 0) {
                log.info("Catalogue seeding complete — {} seed course(s) reconciled back to active=true", reconciled.get());
            }
        };
    }

    private static Iterable<JsonNode> array(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isArray() ? node : java.util.Collections.emptyList();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s.isBlank() ? null : s;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? BigDecimal.ZERO : new BigDecimal(v.asText("0"));
    }
}
