-- PART 3/10: audio lesson media. Safe for databases upgraded from PART 2.
ALTER TABLE course_lessons ADD COLUMN audio_url VARCHAR(600) NULL;