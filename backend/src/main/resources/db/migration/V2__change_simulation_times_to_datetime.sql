DELETE FROM scene_partners;
DELETE FROM scenes;
DELETE FROM question_partners;
DELETE FROM questions;
DELETE FROM relationships;

ALTER TABLE scenes
    MODIFY starts_at DATETIME(6) NOT NULL,
    MODIFY ends_at DATETIME(6) NOT NULL;

ALTER TABLE questions
    MODIFY `time` DATETIME(6) NOT NULL;

ALTER TABLE relationships
    MODIFY update_time DATETIME(6) NOT NULL;
