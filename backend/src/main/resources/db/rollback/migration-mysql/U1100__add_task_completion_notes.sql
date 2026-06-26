-- Input: migration-mysql/V1100__add_task_completion_notes.sql
-- Output: rollback script for mysql environments; review data-loss comments before production use.
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
-- 维护声明: source migration changes must update this rollback script in the same branch.

-- V1100 adds: ALTER TABLE tasks ADD COLUMN completion_notes TEXT;
-- Rollback: drop the column if it exists (safety: only if no data to lose, or data has been backed up)
ALTER TABLE tasks DROP COLUMN IF EXISTS completion_notes;
