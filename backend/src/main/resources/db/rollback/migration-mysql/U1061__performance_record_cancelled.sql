-- Input: V1056__performance_record_cancelled.sql
-- Rollback for V1056__performance_record_cancelled.sql

DROP INDEX idx_performance_cancelled ON performance_record;
ALTER TABLE performance_record
    DROP COLUMN cancelled,
    DROP COLUMN cancelled_at,
    DROP COLUMN cancelled_by,
    DROP COLUMN cancelled_reason;
