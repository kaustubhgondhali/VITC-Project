# VITC Database Design (`vitc_db`)

Connection: MySQL 8+, database `vitc_db`, user `root`, password `root`, InnoDB, `utf8mb4_unicode_ci`.

Files in this folder:

| File | Purpose |
|---|---|
| `vitc_db_fresh.sql` | Drops and recreates `vitc_db` with all 20 tables. Run this first. |
| `seed_data.sql` | Optional demo content (FAQs, testimonials, pricing plans, blog posts). |
| `success_stories_upgrade_part2.sql` | Additive upgrade for an existing DB whose `success_stories` table still has the PART 1/6 shape (`approved` boolean, no `status`/`course_id`/`created_by`). Not needed on a fresh install — `vitc_db_fresh.sql` already has the PART 2/6 shape. |

```bash
# 1. create the database from scratch
mysql -u root -proot < database/vitc_db_fresh.sql

# 2. (optional) demo content
mysql -u root -proot vitc_db < database/seed_data.sql

# 3. start the backend – it seeds the admin + course/assignment catalog
cd backend && mvn spring-boot:run
```

## Type rules (why the old script failed)

Every JPA entity extends `BaseEntity` with a `Long id`, which Hibernate maps to a
**signed `BIGINT`**. The previous script used `BIGINT UNSIGNED` / `INT UNSIGNED`,
so every foreign key mismatched its parent key and MySQL raised
`Error 3780 ... incompatible` plus Hibernate wrong-column-type warnings.

The new script keeps these rules everywhere:

| Java | MySQL |
|---|---|
| `Long` (id, FK, `size_bytes`) | `BIGINT` (signed) |
| `Integer` | `INT` |
| `Boolean` | `BIT(1)` |
| `LocalDateTime` | `DATETIME(6)` |
| `BigDecimal(p,s)` | `DECIMAL(p,s)` |
| enum + `EnumType.STRING` | `VARCHAR(20..30)` |
| `String(length = n)` | `VARCHAR(n)` |
| `@Lob` + `LONGTEXT` | `LONGTEXT` |

## Tables

**Core content:** `courses`, `assignments`, `pricing_plans`, `blog_posts`,
`gallery_items`, `faqs`, `testimonials`, `reviews`, `media_files`.

**People / admin:** `users`, `admins`.

**Leads and orders:** `enrollments`, `assignment_orders`, `contact_messages`,
`job_applications`, `internship_applications`.

**Payments:** `payment_orders`, `payments`, `invoices`.

## Relationships

| Relationship | Type | Implementation |
|---|---|---|
| courses -> enrollments | 1:N | `enrollments.course_id -> courses.id` |
| assignments -> assignment_orders | 1:N | `assignment_orders.assignment_id -> assignments.id` |
| payment_orders -> payments | 1:N | `payments.order_id -> payment_orders.id` (nullable) |
| payment_orders -> invoices | 1:1 | `invoices.order_id -> payment_orders.id` (unique) |
| payments -> invoices | 1:N | `invoices.payment_id -> payments.id` (nullable) |

## Uniqueness

`users.email`, `admins.username`, `admins.email`, `courses.code`,
`assignments.code`, `assignment_orders.order_code`, `payment_orders.order_code`,
`payments.transaction_id`, `invoices.invoice_number`, `invoices.order_id`,
`blog_posts.slug`.

## Seeding on startup

* `AdminSeeder` creates `admin / Admin@123` (`admin@vitc.in`) if neither the
  username nor the email exists — safe to restart any number of times.
* `CatalogSeeder` inserts the course and assignment catalog when those tables
  are empty.
