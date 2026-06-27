-- V121: Add created_by to tasks table for CO-382
-- Records the username of the task creator so the kanban can display "创建人"
-- instead of the stale "系统自动获取" placeholder.

ALTER TABLE tasks
    ADD COLUMN created_by VARCHAR(255) DEFAULT NULL AFTER completion_notes;
