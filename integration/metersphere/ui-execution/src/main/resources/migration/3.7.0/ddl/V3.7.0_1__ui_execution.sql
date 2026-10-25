-- UI execution orchestration (TestHub integration inside MeterSphere)
CREATE TABLE IF NOT EXISTS ui_execution_task (
    id VARCHAR(50) NOT NULL,
    project_id VARCHAR(50) NOT NULL,
    case_id VARCHAR(50) NOT NULL,
    test_plan_id VARCHAR(50) NOT NULL,
    test_plan_case_id VARCHAR(50) NOT NULL,
    case_title VARCHAR(500),
    bdd_given TEXT,
    bdd_when TEXT,
    bdd_then TEXT,
    device_serial VARCHAR(200),
    task_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    summary TEXT,
    failure_reason VARCHAR(64),
    report_url VARCHAR(1000),
    created_by VARCHAR(50),
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ui_exec_task_plan (test_plan_id),
    INDEX idx_ui_exec_task_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS ui_execution_step (
    id VARCHAR(50) NOT NULL,
    task_row_id VARCHAR(50) NOT NULL,
    seq INT NOT NULL,
    status VARCHAR(32),
    decision TEXT,
    action VARCHAR(1000),
    elapsed_ms BIGINT,
    create_time BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_ui_exec_step_task (task_row_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
