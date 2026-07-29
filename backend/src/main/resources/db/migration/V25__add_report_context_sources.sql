ALTER TABLE tasks
    ADD COLUMN blocker_type VARCHAR(30) NULL AFTER hold_reason,
    ADD COLUMN blocker_next_action_type VARCHAR(30) NULL AFTER blocker_type,
    ADD COLUMN blocker_review_date DATE NULL AFTER blocker_next_action_type;

CREATE TABLE weekly_objectives (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    title VARCHAR(120) NOT NULL,
    position INT NOT NULL,
    created_by_member_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_weekly_objectives_group_week_position
        UNIQUE (group_id, week_start, position),
    INDEX idx_weekly_objectives_group_week (group_id, week_start, id),
    CONSTRAINT fk_weekly_objectives_group
        FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_weekly_objectives_creator
        FOREIGN KEY (created_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

CREATE TABLE task_weekly_objective_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    objective_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    linked_by_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_weekly_objective_task_week
        UNIQUE (task_id, week_start),
    INDEX idx_task_weekly_objective_objective (objective_id, task_id),
    CONSTRAINT fk_task_weekly_objective_task
        FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_weekly_objective_objective
        FOREIGN KEY (objective_id) REFERENCES weekly_objectives (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_task_weekly_objective_member
        FOREIGN KEY (linked_by_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

ALTER TABLE task_activity_events
    ADD COLUMN blocker_type VARCHAR(30) NULL AFTER completed_at,
    ADD COLUMN blocker_next_action_type VARCHAR(30) NULL AFTER blocker_type,
    ADD COLUMN blocker_review_date DATE NULL AFTER blocker_next_action_type,
    ADD COLUMN weekly_objective_id BIGINT NULL AFTER blocker_review_date,
    ADD INDEX idx_task_activity_objective (weekly_objective_id, occurred_at),
    ADD CONSTRAINT fk_task_activity_objective
        FOREIGN KEY (weekly_objective_id) REFERENCES weekly_objectives (id)
        ON DELETE SET NULL;

INSERT INTO task_activity_events (
    task_id, group_id, actor_member_id, event_type, occurred_at,
    task_status, task_priority, assignee_member_id, task_created_at,
    due_at, completed_at, blocker_type, blocker_next_action_type,
    blocker_review_date, weekly_objective_id, checklist_total,
    checklist_completed, snapshot_version, history_complete
)
SELECT
    task.id,
    task.group_id,
    NULL,
    'BASELINE',
    UTC_TIMESTAMP(6),
    task.status,
    task.priority,
    task.assignee_member_id,
    task.created_at,
    task.due_at,
    task.completed_at,
    task.blocker_type,
    task.blocker_next_action_type,
    task.blocker_review_date,
    NULL,
    (SELECT COUNT(*) FROM task_checklist_items item WHERE item.task_id = task.id),
    (SELECT COUNT(*) FROM task_checklist_items item
        WHERE item.task_id = task.id AND item.completed = TRUE),
    2,
    CASE WHEN task.status = 'ON_HOLD' THEN FALSE ELSE TRUE END
FROM tasks task;
