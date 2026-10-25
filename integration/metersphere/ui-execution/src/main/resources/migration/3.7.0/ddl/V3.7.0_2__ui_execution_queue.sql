-- Queue, watchdog and failure-report columns for UI execution
ALTER TABLE ui_execution_task
    ADD COLUMN timeout_sec INT NOT NULL DEFAULT 120,
    ADD COLUMN step_timeout_sec INT NOT NULL DEFAULT 30,
    ADD COLUMN dispatched_at BIGINT NULL,
    ADD COLUMN finished_at BIGINT NULL,
    ADD COLUMN queue_position INT NOT NULL DEFAULT 0,
    ADD COLUMN failure_report LONGTEXT NULL;
