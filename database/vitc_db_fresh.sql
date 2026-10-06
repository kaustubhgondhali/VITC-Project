-- ============================================================
--  VITC Website - FRESH MySQL Schema (v3)
--  Database : vitc_db      User : root      Password : root
--  Engine   : InnoDB / utf8mb4
--
--  This script is generated to match the JPA entities EXACTLY:
--    * every primary key is  BIGINT (signed) AUTO_INCREMENT   -> Java Long
--    * every foreign key column is BIGINT (signed)            -> no more
--      "incompatible column types" warnings / Error 3780
--    * enums are VARCHAR (Hibernate EnumType.STRING)
--    * booleans are BIT(1), timestamps are DATETIME(6)
--
--  HOW TO RUN (drops everything and starts from scratch):
--    mysql -u root -proot < database/vitc_db_fresh.sql
--  then start the backend:
--    cd backend && mvn spring-boot:run
-- ============================================================

DROP DATABASE IF EXISTS vitc_db;
CREATE DATABASE vitc_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE vitc_db;

-- ============================================================
-- 1. USERS
-- ============================================================
CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  full_name     VARCHAR(120) NOT NULL,
  email         VARCHAR(150) NOT NULL,
  phone         VARCHAR(20)  DEFAULT NULL,
  password_hash VARCHAR(100) NOT NULL,
  city          VARCHAR(120) DEFAULT NULL,
  role          VARCHAR(30)  NOT NULL DEFAULT 'STUDENT',
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME(6)  DEFAULT NULL,
  updated_at    DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 2. ADMINS
-- ============================================================
CREATE TABLE admins (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  username      VARCHAR(60)  NOT NULL,
  email         VARCHAR(150) NOT NULL,
  full_name     VARCHAR(120) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(30)  NOT NULL DEFAULT 'ADMIN',
  active        BIT(1)       NOT NULL DEFAULT b'1',
  last_login_at DATETIME(6)  DEFAULT NULL,
  session_token VARCHAR(100) DEFAULT NULL,
  session_expires_at DATETIME(6) DEFAULT NULL,
  reset_token   VARCHAR(100) DEFAULT NULL,
  reset_token_expires_at DATETIME(6) DEFAULT NULL,
  created_at    DATETIME(6)  DEFAULT NULL,
  updated_at    DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admins_username (username),
  UNIQUE KEY uk_admins_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. COURSES
-- ============================================================
CREATE TABLE courses (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  code            VARCHAR(60)   NOT NULL,
  title           VARCHAR(150)  NOT NULL,
  description     VARCHAR(2000) DEFAULT NULL,
  price           DECIMAL(10,2) NOT NULL,
  meta            VARCHAR(120)  DEFAULT NULL,
  level           VARCHAR(60)   DEFAULT NULL,
  category        VARCHAR(60)   DEFAULT NULL,
  duration_months VARCHAR(40)   DEFAULT NULL,
  icon            VARCHAR(20)   DEFAULT NULL,
  active          BIT(1)        NOT NULL DEFAULT b'1',
  created_at      DATETIME(6)   DEFAULT NULL,
  updated_at      DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_courses_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. ASSIGNMENTS
-- ============================================================
CREATE TABLE assignments (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  code          VARCHAR(60)   NOT NULL,
  title         VARCHAR(180)  NOT NULL,
  description   VARCHAR(2000) DEFAULT NULL,
  price         DECIMAL(10,2) NOT NULL,
  tech          VARCHAR(120)  DEFAULT NULL,
  difficulty    VARCHAR(40)   DEFAULT NULL,
  delivery_days VARCHAR(40)   DEFAULT NULL,
  category      VARCHAR(60)   DEFAULT NULL,
  icon          VARCHAR(20)   DEFAULT NULL,
  features      VARCHAR(2000) DEFAULT NULL,
  active        BIT(1)        NOT NULL DEFAULT b'1',
  project_file_path    VARCHAR(255) DEFAULT NULL,
  project_file_name    VARCHAR(160) DEFAULT NULL,
  project_content_type VARCHAR(120) DEFAULT NULL,
  project_size_bytes   BIGINT       DEFAULT NULL,
  project_uploaded_at  DATETIME(6)  DEFAULT NULL,
  created_at    DATETIME(6)   DEFAULT NULL,
  updated_at    DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_assignments_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. ENROLLMENTS  (FK -> courses.id)
-- ============================================================
CREATE TABLE enrollments (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  student_name VARCHAR(120)  NOT NULL,
  email        VARCHAR(150)  NOT NULL,
  phone        VARCHAR(20)   NOT NULL,
  college      VARCHAR(150)  DEFAULT NULL,
  course_id    BIGINT        NOT NULL,
  amount       DECIMAL(10,2) DEFAULT NULL,
  status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  notes        VARCHAR(1000) DEFAULT NULL,
  created_at   DATETIME(6)   DEFAULT NULL,
  updated_at   DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_enrollments_course (course_id),
  CONSTRAINT fk_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. ASSIGNMENT_ORDERS  (FK -> assignments.id)
-- ============================================================
CREATE TABLE assignment_orders (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  order_code    VARCHAR(40)   NOT NULL,
  assignment_id BIGINT        NOT NULL,
  student_name  VARCHAR(120)  NOT NULL,
  email         VARCHAR(150)  NOT NULL,
  phone         VARCHAR(20)   NOT NULL,
  college       VARCHAR(150)  DEFAULT NULL,
  amount        DECIMAL(10,2) DEFAULT NULL,
  status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  requirements  VARCHAR(1000) DEFAULT NULL,
  delivery_file_path    VARCHAR(255)  DEFAULT NULL,
  delivery_file_name    VARCHAR(160)  DEFAULT NULL,
  delivery_content_type VARCHAR(120)  DEFAULT NULL,
  delivery_size_bytes   BIGINT        DEFAULT NULL,
  delivery_note         VARCHAR(1000) DEFAULT NULL,
  delivered_at          DATETIME(6)   DEFAULT NULL,
  download_token_hash   VARCHAR(64)   DEFAULT NULL,
  download_expires_at   DATETIME(6)   DEFAULT NULL,
  download_count        INT           DEFAULT NULL,
  last_downloaded_at    DATETIME(6)   DEFAULT NULL,
  created_at    DATETIME(6)   DEFAULT NULL,
  updated_at    DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_assignment_orders_code (order_code),
  KEY idx_assignment_orders_assignment (assignment_id),
  KEY idx_assignment_orders_download_token (download_token_hash),
  CONSTRAINT fk_assignment_orders_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 7. PAYMENT_ORDERS
-- ============================================================
CREATE TABLE payment_orders (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  order_code      VARCHAR(40)   NOT NULL,
  item_type       VARCHAR(20)   NOT NULL,
  item_ref_id     BIGINT        DEFAULT NULL,
  item_title      VARCHAR(200)  NOT NULL,
  item_meta       VARCHAR(250)  DEFAULT NULL,
  customer_name   VARCHAR(120)  NOT NULL,
  email           VARCHAR(150)  NOT NULL,
  phone           VARCHAR(20)   DEFAULT NULL,
  city            VARCHAR(120)  DEFAULT NULL,
  coupon_code     VARCHAR(40)   DEFAULT NULL,
  subtotal        DECIMAL(10,2) NOT NULL,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  tax_rate        DECIMAL(5,2)  NOT NULL DEFAULT 18.00,
  tax_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  total_amount    DECIMAL(10,2) NOT NULL,
  currency        VARCHAR(3)    NOT NULL DEFAULT 'INR',
  status          VARCHAR(20)   NOT NULL DEFAULT 'CREATED',
  notes           VARCHAR(1000) DEFAULT NULL,
  created_at      DATETIME(6)   DEFAULT NULL,
  updated_at      DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_orders_code (order_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 8. PAYMENTS  (FK -> payment_orders.id)
-- ============================================================
CREATE TABLE payments (
  id                  BIGINT        NOT NULL AUTO_INCREMENT,
  reference_type      VARCHAR(40)   DEFAULT NULL,
  reference_id        BIGINT        DEFAULT NULL,
  order_id            BIGINT        DEFAULT NULL,
  transaction_id      VARCHAR(80)   DEFAULT NULL,
  payer_name          VARCHAR(120)  NOT NULL,
  email               VARCHAR(150)  NOT NULL,
  amount              DECIMAL(10,2) NOT NULL,
  currency            VARCHAR(3)    NOT NULL DEFAULT 'INR',
  method              VARCHAR(20)   NOT NULL,
  method_detail       VARCHAR(120)  DEFAULT NULL,
  status              VARCHAR(20)   NOT NULL DEFAULT 'INITIATED',
  provider            VARCHAR(30)   DEFAULT 'MOCK',
  provider_order_id   VARCHAR(120)  DEFAULT NULL,
  provider_payment_id VARCHAR(120)  DEFAULT NULL,
  provider_signature  VARCHAR(255)  DEFAULT NULL,
  gateway_response    VARCHAR(2000) DEFAULT NULL,
  failure_reason      VARCHAR(300)  DEFAULT NULL,
  paid_at             DATETIME(6)   DEFAULT NULL,
  created_at          DATETIME(6)   DEFAULT NULL,
  updated_at          DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payments_transaction (transaction_id),
  KEY idx_payments_order (order_id),
  CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES payment_orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 9. INVOICES  (FK -> payment_orders.id, payments.id)
-- ============================================================
CREATE TABLE invoices (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  invoice_number  VARCHAR(40)   NOT NULL,
  order_id        BIGINT        NOT NULL,
  payment_id      BIGINT        DEFAULT NULL,
  billing_name    VARCHAR(140)  NOT NULL,
  billing_email   VARCHAR(160)  NOT NULL,
  billing_phone   VARCHAR(20)   DEFAULT NULL,
  billing_address VARCHAR(400)  DEFAULT NULL,
  subtotal        DECIMAL(10,2) NOT NULL,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  tax_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  total_amount    DECIMAL(10,2) NOT NULL,
  gst_number      VARCHAR(30)   DEFAULT NULL,
  status          VARCHAR(20)   NOT NULL DEFAULT 'ISSUED',
  issued_at       DATETIME(6)   NOT NULL,
  created_at      DATETIME(6)   DEFAULT NULL,
  updated_at      DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoices_number (invoice_number),
  UNIQUE KEY uk_invoices_order (order_id),
  KEY idx_invoices_payment (payment_id),
  CONSTRAINT fk_invoices_order   FOREIGN KEY (order_id)   REFERENCES payment_orders (id),
  CONSTRAINT fk_invoices_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 10. CONTACT_MESSAGES
-- ============================================================
CREATE TABLE contact_messages (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  name       VARCHAR(120)  NOT NULL,
  email      VARCHAR(150)  NOT NULL,
  phone      VARCHAR(20)   DEFAULT NULL,
  subject    VARCHAR(180)  DEFAULT NULL,
  message    VARCHAR(2000) NOT NULL,
  handled    BIT(1)        NOT NULL DEFAULT b'0',
  created_at DATETIME(6)   DEFAULT NULL,
  updated_at DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 11. TESTIMONIALS
-- ============================================================
CREATE TABLE testimonials (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  name       VARCHAR(120)  NOT NULL,
  role       VARCHAR(120)  DEFAULT NULL,
  photo_url  VARCHAR(400)  DEFAULT NULL,
  rating     INT           NOT NULL,
  message    VARCHAR(1500) NOT NULL,
  approved   BIT(1)        NOT NULL DEFAULT b'0',
  created_at DATETIME(6)   DEFAULT NULL,
  updated_at DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 12. REVIEWS
-- ============================================================
CREATE TABLE reviews (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  reviewer_name VARCHAR(120)  NOT NULL,
  email         VARCHAR(150)  NOT NULL,
  course_code   VARCHAR(60)   DEFAULT NULL,
  course_title  VARCHAR(150)  DEFAULT NULL,
  rating        INT           NOT NULL,
  comment       VARCHAR(2000) NOT NULL,
  image_url     VARCHAR(500)  DEFAULT NULL,
  approved      BIT(1)        NOT NULL DEFAULT b'0',
  featured      BIT(1)        NOT NULL DEFAULT b'0',
  created_at    DATETIME(6)   DEFAULT NULL,
  updated_at    DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 13. FAQS
-- ============================================================
CREATE TABLE faqs (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  question      VARCHAR(300)  NOT NULL,
  answer        VARCHAR(4000) NOT NULL,
  category      VARCHAR(60)   DEFAULT NULL,
  display_order INT           NOT NULL DEFAULT 0,
  active        BIT(1)        NOT NULL DEFAULT b'1',
  created_at    DATETIME(6)   DEFAULT NULL,
  updated_at    DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 14. BLOG_POSTS
-- ============================================================
CREATE TABLE blog_posts (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  slug            VARCHAR(180) NOT NULL,
  title           VARCHAR(200) NOT NULL,
  excerpt         VARCHAR(500) DEFAULT NULL,
  content         LONGTEXT     NOT NULL,
  cover_image_url VARCHAR(400) DEFAULT NULL,
  author          VARCHAR(120) DEFAULT NULL,
  tags            VARCHAR(300) DEFAULT NULL,
  published       BIT(1)       NOT NULL DEFAULT b'0',
  published_at    DATETIME(6)  DEFAULT NULL,
  created_at      DATETIME(6)  DEFAULT NULL,
  updated_at      DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_blog_posts_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 15. GALLERY_ITEMS
-- ============================================================
CREATE TABLE gallery_items (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  title      VARCHAR(180) NOT NULL,
  image_url  VARCHAR(400) NOT NULL,
  category   VARCHAR(60)  DEFAULT NULL,
  caption    VARCHAR(500) DEFAULT NULL,
  created_at DATETIME(6)  DEFAULT NULL,
  updated_at DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 16. PRICING_PLANS
-- ============================================================
CREATE TABLE pricing_plans (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  name           VARCHAR(120)  NOT NULL,
  tagline        VARCHAR(300)  DEFAULT NULL,
  price          DECIMAL(12,2) NOT NULL,
  old_price      DECIMAL(12,2) DEFAULT NULL,
  currency       VARCHAR(40)   DEFAULT NULL,
  billing_period VARCHAR(40)   DEFAULT NULL,
  features       VARCHAR(4000) DEFAULT NULL,
  category       VARCHAR(60)   DEFAULT NULL,
  display_order  INT           NOT NULL DEFAULT 0,
  highlighted    BIT(1)        NOT NULL DEFAULT b'0',
  active         BIT(1)        NOT NULL DEFAULT b'1',
  created_at     DATETIME(6)   DEFAULT NULL,
  updated_at     DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 17. JOB_APPLICATIONS
-- ============================================================
CREATE TABLE job_applications (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  full_name        VARCHAR(120)  NOT NULL,
  email            VARCHAR(150)  NOT NULL,
  phone            VARCHAR(20)   NOT NULL,
  position         VARCHAR(120)  NOT NULL,
  experience_years INT           DEFAULT NULL,
  resume_url       VARCHAR(400)  DEFAULT NULL,
  message          VARCHAR(1500) DEFAULT NULL,
  status           VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED',
  job_requirement_id    BIGINT        DEFAULT NULL,
  company_name          VARCHAR(150)  DEFAULT NULL,
  current_city          VARCHAR(120)  DEFAULT NULL,
  date_of_birth         DATE          DEFAULT NULL,
  gender                VARCHAR(30)   DEFAULT NULL,
  highest_qualification VARCHAR(150)  DEFAULT NULL,
  institution           VARCHAR(200)  DEFAULT NULL,
  graduation_year       INT           DEFAULT NULL,
  academic_score        VARCHAR(30)   DEFAULT NULL,
  current_company       VARCHAR(150)  DEFAULT NULL,
  current_ctc           VARCHAR(60)   DEFAULT NULL,
  expected_ctc          VARCHAR(60)   DEFAULT NULL,
  notice_period         VARCHAR(60)   DEFAULT NULL,
  skills                VARCHAR(1000) DEFAULT NULL,
  linkedin_url          VARCHAR(300)  DEFAULT NULL,
  portfolio_url         VARCHAR(300)  DEFAULT NULL,
  resume_file_path      VARCHAR(255)  DEFAULT NULL,
  resume_file_name      VARCHAR(160)  DEFAULT NULL,
  resume_content_type   VARCHAR(120)  DEFAULT NULL,
  resume_size_bytes     BIGINT        DEFAULT NULL,
  admin_notes           VARCHAR(1000) DEFAULT NULL,
  created_at       DATETIME(6)   DEFAULT NULL,
  updated_at       DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_job_applications_job (job_requirement_id),
  KEY idx_job_applications_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 18. INTERNSHIP_APPLICATIONS
-- ============================================================
CREATE TABLE internship_applications (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  full_name  VARCHAR(120)  NOT NULL,
  email      VARCHAR(150)  NOT NULL,
  phone      VARCHAR(20)   NOT NULL,
  college    VARCHAR(150)  DEFAULT NULL,
  domain     VARCHAR(120)  NOT NULL,
  duration   VARCHAR(60)   DEFAULT NULL,
  resume_url VARCHAR(400)  DEFAULT NULL,
  message    VARCHAR(1500) DEFAULT NULL,
  status     VARCHAR(20)   NOT NULL DEFAULT 'SUBMITTED',
  created_at DATETIME(6)   DEFAULT NULL,
  updated_at DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 19. MEDIA_FILES
-- ============================================================
CREATE TABLE media_files (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  file_name     VARCHAR(260) NOT NULL,
  original_name VARCHAR(260) NOT NULL,
  content_type  VARCHAR(120) DEFAULT NULL,
  size_bytes    BIGINT       NOT NULL,
  url           VARCHAR(400) NOT NULL,
  file_type     VARCHAR(20)  NOT NULL,
  folder        VARCHAR(60)  DEFAULT NULL,
  uploaded_by   VARCHAR(60)  DEFAULT NULL,
  created_at    DATETIME(6)  DEFAULT NULL,
  updated_at    DATETIME(6)  DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 20. SUCCESS_STORIES (PART 1/6 + PART 2/6 — video success stories,
--     student/teacher/parent, replaces the plain-text testimonials tab
--     on the public "Success Stories" page; testimonials table above is
--     untouched)
-- ============================================================
CREATE TABLE success_stories (
  id               BIGINT        NOT NULL AUTO_INCREMENT,
  name             VARCHAR(120)  NOT NULL,
  category         VARCHAR(30)   NOT NULL,
  course_program   VARCHAR(150)  DEFAULT NULL,
  course_id        BIGINT        DEFAULT NULL,
  designation      VARCHAR(150)  DEFAULT NULL,
  description      VARCHAR(500)  DEFAULT NULL,
  video_url        VARCHAR(500)  NOT NULL,
  thumbnail_url    VARCHAR(500)  DEFAULT NULL,
  status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
  display_order    INT           NOT NULL DEFAULT 0,
  created_by       VARCHAR(120)  DEFAULT NULL,
  created_by_role  VARCHAR(20)   DEFAULT NULL,
  created_at       DATETIME(6)   DEFAULT NULL,
  updated_at       DATETIME(6)   DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- Done. Start the backend next; the app seeds the default admin
-- (admin / Admin@123) and the course + assignment catalog itself.
-- ============================================================
SELECT 'vitc_db created successfully' AS status;
