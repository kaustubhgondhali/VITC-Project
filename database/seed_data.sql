-- ============================================================
--  VITC Website - OPTIONAL DEMO DATA (v3)
--  Run AFTER vitc_db_fresh.sql (and it is safe to run after the
--  backend has started at least once).
--    mysql -u root -proot vitc_db < database/seed_data.sql
--  Note: the backend already seeds the default admin and the
--  course/assignment catalog on startup, so this file only adds
--  extra front-of-site content (testimonials, faqs, blog, plans).
-- ============================================================
USE vitc_db;

INSERT INTO faqs (question, answer, category, display_order, active, created_at, updated_at) VALUES
('Are the courses beginner friendly?','Yes. Every course starts from fundamentals and moves to project work, so no prior experience is required.','Courses',1,b'1',NOW(6),NOW(6)),
('Do you provide a certificate?','Yes, a verifiable completion certificate is issued after the final project review.','Courses',2,b'1',NOW(6),NOW(6)),
('How are assignments delivered?','Source code, documentation and a walkthrough are shared over email within the delivery window shown on the assignment.','Assignments',3,b'1',NOW(6),NOW(6)),
('Which payment methods are supported?','UPI, cards, netbanking and wallets. An invoice is generated automatically after every successful payment.','Payments',4,b'1',NOW(6),NOW(6));

INSERT INTO testimonials (name, role, photo_url, rating, message, approved, created_at, updated_at) VALUES
('Aarti Sharma','Full Stack Trainee',NULL,5,'The mentors reviewed my code line by line. I cleared two interviews within a month of finishing the course.',b'1',NOW(6),NOW(6)),
('Rahul Verma','Final Year Student',NULL,5,'Got my project assignment delivered ahead of schedule with clean documentation. Highly recommended.',b'1',NOW(6),NOW(6)),
('Sneha Patil','Data Analyst Intern',NULL,4,'Loved the practical, dataset-first approach. The dashboards I built here went straight into my portfolio.',b'1',NOW(6),NOW(6));

INSERT INTO pricing_plans (name, tagline, price, old_price, currency, billing_period, features, category, display_order, highlighted, active, created_at, updated_at) VALUES
('Starter','Learn one technology end to end',4999.00,7999.00,'INR','one-time','Live classes|Recorded sessions|1 capstone project|Certificate','Courses',1,b'0',b'1',NOW(6),NOW(6)),
('Professional','Most popular career track',9999.00,14999.00,'INR','one-time','Everything in Starter|3 projects|Resume + LinkedIn review|Mock interviews','Courses',2,b'1',b'1',NOW(6),NOW(6)),
('Placement Pro','Training plus placement support',15999.00,21999.00,'INR','one-time','Everything in Professional|Internship letter|Unlimited mock interviews|Placement referrals','Courses',3,b'0',b'1',NOW(6),NOW(6));

INSERT INTO blog_posts (slug, title, excerpt, content, cover_image_url, author, tags, published, published_at, created_at, updated_at) VALUES
('roadmap-to-full-stack-2026','A Practical Roadmap to Full Stack Development in 2026','Skip tutorial hell: the exact order to learn HTML, CSS, JavaScript, React, Spring Boot and MySQL.','<p>Start with the fundamentals of HTML and CSS, then spend real time on JavaScript before touching a framework. Once you can build a page and talk to an API, move to React for the interface and Spring Boot with MySQL for the backend. Finish with deployment, because a project nobody can open is not a project.</p>',NULL,'VITC Team','fullstack,career,roadmap',b'1',NOW(6),NOW(6),NOW(6)),
('choosing-your-final-year-project','How to Choose a Final Year Project That Actually Gets You Hired','A good project solves a small problem completely instead of solving a big problem halfway.','<p>Pick a domain you can explain in one sentence, keep the scope to three core features, and document every decision. Reviewers care far more about a working demo and clean documentation than about a long feature list.</p>',NULL,'VITC Team','projects,students,placement',b'1',NOW(6),NOW(6),NOW(6));

SELECT 'demo data inserted' AS status;
