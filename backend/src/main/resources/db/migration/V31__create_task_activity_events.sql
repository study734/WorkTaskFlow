CREATE TABLE task_activity_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    actor_member_id BIGINT NULL,
    event_type VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    task_status VARCHAR(20) NOT NULL,
    task_priority VARCHAR(20) NOT NULL,
    assignee_member_id BIGINT NULL,
    task_created_at DATETIME(6) NOT NULL,
    due_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    checklist_total INT NOT NULL,
    checklist_completed INT NOT NULL,
    snapshot_version INT NOT NULL,
    history_complete BOOLEAN NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_task_activity_group_occurred (group_id, occurred_at, id),
    INDEX idx_task_activity_task_occurred (task_id, occurred_at, id),
    CONSTRAINT fk_task_activity_task
        FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_activity_group
        FOREIGN KEY (group_id) REFERENCES work_groups (id),
    CONSTRAINT fk_task_activity_actor
        FOREIGN KEY (actor_member_id) REFERENCES group_members (id),
    CONSTRAINT fk_task_activity_assignee
        FOREIGN KEY (assignee_member_id) REFERENCES group_members (id)
) ENGINE = InnoDB;

INSERT INTO task_activity_events (
    task_id, group_id, actor_member_id, event_type, occurred_at,
    task_status, task_priority, assignee_member_id, task_created_at,
    due_at, completed_at, checklist_total, checklist_completed,
    snapshot_version, history_complete
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
    (SELECT COUNT(*) FROM task_checklist_items item WHERE item.task_id = task.id),
    (SELECT COUNT(*) FROM task_checklist_items item
        WHERE item.task_id = task.id AND item.completed = TRUE),
    1,
    FALSE
FROM tasks task;
