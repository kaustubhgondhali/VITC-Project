-- ============================================================================
-- VITC — Job Portal applications (Apply button -> application form -> Admin)
-- Additive only. No existing row is modified or dropped.
--
-- WHO NEEDS THIS: environments running with spring.jpa.hibernate.ddl-auto=validate
-- (the prod profile), which refuse to start until these columns exist. With the
-- default ddl-auto=update the backend adds them itself on startup.
--
-- Run ONCE against an existing vitc_db. MySQL has no "ADD COLUMN IF NOT EXISTS",
-- so re-running the ALTER below fails harmlessly with "Duplicate column name".
-- Resumes are stored as files in app.job.resume-dir (private), not in the database.
-- ============================================================================

ALTER TABLE job_applications
  ADD COLUMN job_requirement_id    BIGINT        NULL,  -- plain id, no FK: deleting a job never fails
  ADD COLUMN company_name          VARCHAR(150)  NULL,  -- snapshot of the company applied to
  ADD COLUMN current_city          VARCHAR(120)  NULL,
  ADD COLUMN date_of_birth         DATE          NULL,
  ADD COLUMN gender                VARCHAR(30)   NULL,
  ADD COLUMN highest_qualification VARCHAR(150)  NULL,
  ADD COLUMN institution           VARCHAR(200)  NULL,
  ADD COLUMN graduation_year       INT           NULL,
  ADD COLUMN academic_score        VARCHAR(30)   NULL,
  ADD COLUMN current_company       VARCHAR(150)  NULL,
  ADD COLUMN current_ctc           VARCHAR(60)   NULL,
  ADD COLUMN expected_ctc          VARCHAR(60)   NULL,
  ADD COLUMN notice_period         VARCHAR(60)   NULL,
  ADD COLUMN skills                VARCHAR(1000) NULL,
  ADD COLUMN linkedin_url          VARCHAR(300)  NULL,
  ADD COLUMN portfolio_url         VARCHAR(300)  NULL,
  ADD COLUMN resume_file_path      VARCHAR(255)  NULL,
  ADD COLUMN resume_file_name      VARCHAR(160)  NULL,
  ADD COLUMN resume_content_type   VARCHAR(120)  NULL,
  ADD COLUMN resume_size_bytes     BIGINT        NULL,
  ADD COLUMN admin_notes           VARCHAR(1000) NULL;

CREATE INDEX idx_job_applications_job ON job_applications (job_requirement_id);
CREATE INDEX idx_job_applications_email ON job_applications (email);
