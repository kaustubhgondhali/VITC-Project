-- Additive migration for course completion exams and public job portal.
CREATE TABLE IF NOT EXISTS exams (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  enrollment_id BIGINT NOT NULL UNIQUE,
  status VARCHAR(30) NOT NULL DEFAULT 'READY_FOR_EXAM', completion_date DATE NOT NULL,
  exam_date DATE NULL, exam_time TIME NULL, mode VARCHAR(40) NULL,
  location_or_link VARCHAR(255) NULL, notes VARCHAR(1000) NULL,
  created_at DATETIME NULL, updated_at DATETIME NULL,
  CONSTRAINT fk_exam_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id)
);
CREATE INDEX idx_exam_enrollment ON exams(enrollment_id);
CREATE TABLE IF NOT EXISTS job_requirements (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, job_title VARCHAR(150) NOT NULL,
  company_name VARCHAR(150) NOT NULL, description VARCHAR(3000) NOT NULL,
  required_skills VARCHAR(1000) NOT NULL, experience VARCHAR(100), qualification VARCHAR(150),
  location VARCHAR(150), employment_type VARCHAR(80), salary_ctc VARCHAR(100), openings INT,
  application_deadline DATE, application_method VARCHAR(500), published BIT NOT NULL DEFAULT 0,
  created_at DATETIME NULL, updated_at DATETIME NULL
);
CREATE INDEX idx_job_public ON job_requirements(published);
CREATE TABLE IF NOT EXISTS employer_enquiries (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, company_name VARCHAR(150) NOT NULL,
  contact_person VARCHAR(120) NOT NULL, email VARCHAR(150) NOT NULL, phone VARCHAR(20) NOT NULL,
  job_title VARCHAR(150) NOT NULL, openings INT, required_skills VARCHAR(1000),
  experience_required VARCHAR(100), location VARCHAR(150), message VARCHAR(3000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'NEW', created_at DATETIME NULL, updated_at DATETIME NULL
);
CREATE INDEX idx_enquiry_status ON employer_enquiries(status);
