SET NAMES utf8mb4;
SET time_zone = '+00:00';
START TRANSACTION;

CREATE TEMPORARY TABLE demo_user_ids (id BIGINT PRIMARY KEY);
INSERT INTO demo_user_ids (id)
SELECT id FROM users WHERE username IN ('demo_leader', 'demo_member', 'demo_viewer');

CREATE TEMPORARY TABLE demo_group_ids (id BIGINT PRIMARY KEY);
INSERT INTO demo_group_ids (id)
SELECT id FROM work_groups WHERE created_by IN (SELECT id FROM demo_user_ids);

CREATE TEMPORARY TABLE demo_task_ids (id BIGINT PRIMARY KEY);
INSERT INTO demo_task_ids (id)
SELECT id FROM tasks WHERE group_id IN (SELECT id FROM demo_group_ids);

CREATE TEMPORARY TABLE demo_comment_ids (id BIGINT PRIMARY KEY);
INSERT INTO demo_comment_ids (id)
SELECT id FROM task_comments WHERE task_id IN (SELECT id FROM demo_task_ids);

DELETE FROM notifications WHERE recipient_user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM notifications WHERE actor_user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM notifications WHERE group_id IN (SELECT id FROM demo_group_ids);
DELETE FROM notifications WHERE task_id IN (SELECT id FROM demo_task_ids);
DELETE FROM notifications WHERE comment_id IN (SELECT id FROM demo_comment_ids);
DELETE FROM comment_mentions WHERE comment_id IN (SELECT id FROM demo_comment_ids);
DELETE FROM task_comments WHERE id IN (SELECT id FROM demo_comment_ids);
DELETE FROM task_checklist_items WHERE task_id IN (SELECT id FROM demo_task_ids);
DELETE FROM task_status_histories WHERE task_id IN (SELECT id FROM demo_task_ids);
DELETE FROM tasks WHERE id IN (SELECT id FROM demo_task_ids);
DELETE FROM calendar_events WHERE group_id IN (SELECT id FROM demo_group_ids);
DELETE FROM group_invitations WHERE group_id IN (SELECT id FROM demo_group_ids);
DELETE FROM group_members WHERE group_id IN (SELECT id FROM demo_group_ids);
DELETE FROM work_groups WHERE id IN (SELECT id FROM demo_group_ids);
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM social_accounts WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM user_consents WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM one_time_tokens WHERE email IN (
  'demo-leader@local.test', 'demo-member@local.test', 'demo-viewer@local.test'
);
DELETE FROM users WHERE id IN (SELECT id FROM demo_user_ids);

SET @demo_password_hash = '$2a$10$ugDPXuFKda.H18X4in3JUe3dzrfOHPiCt30WaIFIyLzTXbSO4nZhi';
SET @now = UTC_TIMESTAMP(6);

INSERT INTO users (username, email, password_hash, name, role, email_verified, nickname,
                   phone_number, profile_image_url, status, created_at, updated_at)
VALUES
  ('demo_leader', 'demo-leader@local.test', @demo_password_hash, '김팀장', 'USER', TRUE,
   '김팀장', '010-1000-1000', NULL, 'ACTIVE', @now, @now),
  ('demo_member', 'demo-member@local.test', @demo_password_hash, '이팀원', 'USER', TRUE,
   '이팀원', '010-2000-2000', NULL, 'ACTIVE', @now, @now),
  ('demo_viewer', 'demo-viewer@local.test', @demo_password_hash, '박팀원', 'USER', TRUE,
   '박팀원', '010-3000-3000', NULL, 'ACTIVE', @now, @now);

SET @leader_user = (SELECT id FROM users WHERE username = 'demo_leader');
SET @member_user = (SELECT id FROM users WHERE username = 'demo_member');
SET @viewer_user = (SELECT id FROM users WHERE username = 'demo_viewer');

INSERT INTO work_groups (type, name, description, timezone, dashboard_visibility,
                         created_by, created_at, updated_at)
VALUES
  ('PERSONAL', '김팀장의 개인 일정', '개인 일정 시연', 'Asia/Seoul', 'MEMBERS',
   @leader_user, @now, @now),
  ('PERSONAL', '이팀원의 개인 일정', NULL, 'Asia/Seoul', 'MEMBERS',
   @member_user, @now, @now),
  ('PERSONAL', '박팀원의 개인 일정', NULL, 'Asia/Seoul', 'MEMBERS',
   @viewer_user, @now, @now),
  ('TEAM', '로컬 알파 시연팀', '발표와 로컬 기능 검증을 위한 재현 가능한 팀',
   'Asia/Seoul', 'MEMBERS', @leader_user, @now, @now);

SET @leader_personal_group = (SELECT id FROM work_groups WHERE created_by = @leader_user AND type = 'PERSONAL');
SET @member_personal_group = (SELECT id FROM work_groups WHERE created_by = @member_user AND type = 'PERSONAL');
SET @viewer_personal_group = (SELECT id FROM work_groups WHERE created_by = @viewer_user AND type = 'PERSONAL');
SET @team_group = (SELECT id FROM work_groups WHERE created_by = @leader_user AND type = 'TEAM');

INSERT INTO group_members (group_id, user_id, role, status, joined_at)
VALUES
  (@leader_personal_group, @leader_user, 'LEADER', 'ACTIVE', @now),
  (@member_personal_group, @member_user, 'LEADER', 'ACTIVE', @now),
  (@viewer_personal_group, @viewer_user, 'LEADER', 'ACTIVE', @now),
  (@team_group, @leader_user, 'LEADER', 'ACTIVE', @now),
  (@team_group, @member_user, 'MEMBER', 'ACTIVE', @now),
  (@team_group, @viewer_user, 'MEMBER', 'ACTIVE', @now);

SET @leader_member = (SELECT id FROM group_members WHERE group_id = @team_group AND user_id = @leader_user);
SET @member_member = (SELECT id FROM group_members WHERE group_id = @team_group AND user_id = @member_user);
SET @viewer_member = (SELECT id FROM group_members WHERE group_id = @team_group AND user_id = @viewer_user);
SET @leader_personal_member = (SELECT id FROM group_members WHERE group_id = @leader_personal_group);

INSERT INTO tasks (group_id, requester_member_id, approver_member_id, assignee_member_id,
                   title, description, priority, status, start_at, due_at, completed_at,
                   hold_reason, stop_reason, created_at, updated_at, version)
VALUES
  (@team_group, @member_member, NULL, NULL,
   '신규 기능 제안 검토', '팀원이 제안하고 팀장이 승인하는 흐름을 시연합니다.',
   'HIGH', 'REQUESTED', NULL, DATE_ADD(@now, INTERVAL 3 DAY), NULL,
   NULL, NULL, DATE_SUB(@now, INTERVAL 2 HOUR), DATE_SUB(@now, INTERVAL 2 HOUR), 0),
  (@team_group, @leader_member, @leader_member, @member_member,
   '발표 자료 초안 작성', '담당자 지정 후 시작 전인 업무입니다.',
   'NORMAL', 'TODO', NULL, DATE_ADD(@now, INTERVAL 2 DAY), NULL,
   NULL, NULL, DATE_SUB(@now, INTERVAL 3 DAY), DATE_SUB(@now, INTERVAL 2 DAY), 2),
  (@team_group, @member_member, @leader_member, @member_member,
   '모바일 화면 최종 점검', '체크리스트와 댓글·멘션을 함께 보여 주는 진행 중 업무입니다.',
   'URGENT', 'IN_PROGRESS', DATE_SUB(@now, INTERVAL 1 DAY), DATE_ADD(@now, INTERVAL 1 DAY), NULL,
   NULL, NULL, DATE_SUB(@now, INTERVAL 4 DAY), DATE_SUB(@now, INTERVAL 1 HOUR), 3),
  (@team_group, @viewer_member, @leader_member, @viewer_member,
   '외부 피드백 반영', '보류 사유와 재개 흐름을 시연합니다.',
   'HIGH', 'ON_HOLD', DATE_SUB(@now, INTERVAL 2 DAY), DATE_ADD(@now, INTERVAL 4 DAY), NULL,
   '디자인 확인 회신 대기', NULL, DATE_SUB(@now, INTERVAL 5 DAY), DATE_SUB(@now, INTERVAL 6 HOUR), 4),
  (@team_group, @leader_member, @leader_member, @leader_member,
   'API 계약 검토 완료', '완료 업무와 기한 준수율에 포함됩니다.',
   'NORMAL', 'COMPLETED', DATE_SUB(@now, INTERVAL 5 DAY), DATE_SUB(@now, INTERVAL 1 DAY),
   DATE_SUB(@now, INTERVAL 2 DAY), NULL, NULL,
   DATE_SUB(@now, INTERVAL 7 DAY), DATE_SUB(@now, INTERVAL 2 DAY), 4),
  (@team_group, @leader_member, @leader_member, @member_member,
   '지연 업무 우선 처리', '마감일이 지나 대시보드 위험 업무에 표시됩니다.',
   'URGENT', 'TODO', NULL, DATE_SUB(@now, INTERVAL 2 DAY), NULL,
   NULL, NULL, DATE_SUB(@now, INTERVAL 6 DAY), DATE_SUB(@now, INTERVAL 2 DAY), 2),
  (@leader_personal_group, @leader_personal_member, @leader_personal_member, @leader_personal_member,
   '개인 발표 리허설', '개인 일정과 통합 캘린더를 시연합니다.',
   'LOW', 'TODO', NULL, DATE_ADD(@now, INTERVAL 5 DAY), NULL,
   NULL, NULL, DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 1 DAY), 0);

SET @requested_task = (SELECT id FROM tasks WHERE group_id = @team_group AND title = '신규 기능 제안 검토');
SET @todo_task = (SELECT id FROM tasks WHERE group_id = @team_group AND title = '발표 자료 초안 작성');
SET @progress_task = (SELECT id FROM tasks WHERE group_id = @team_group AND title = '모바일 화면 최종 점검');
SET @hold_task = (SELECT id FROM tasks WHERE group_id = @team_group AND title = '외부 피드백 반영');
SET @completed_task = (SELECT id FROM tasks WHERE group_id = @team_group AND title = 'API 계약 검토 완료');
SET @delayed_task = (SELECT id FROM tasks WHERE group_id = @team_group AND title = '지연 업무 우선 처리');
SET @personal_task = (SELECT id FROM tasks WHERE group_id = @leader_personal_group AND title = '개인 발표 리허설');

INSERT INTO task_status_histories (task_id, from_status, to_status, changed_by_member_id, reason, created_at)
VALUES
  (@requested_task, NULL, 'REQUESTED', @member_member, NULL, DATE_SUB(@now, INTERVAL 2 HOUR)),
  (@todo_task, NULL, 'REQUESTED', @leader_member, NULL, DATE_SUB(@now, INTERVAL 3 DAY)),
  (@todo_task, 'REQUESTED', 'TODO', @leader_member, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
  (@progress_task, NULL, 'REQUESTED', @member_member, NULL, DATE_SUB(@now, INTERVAL 4 DAY)),
  (@progress_task, 'REQUESTED', 'TODO', @leader_member, NULL, DATE_SUB(@now, INTERVAL 3 DAY)),
  (@progress_task, 'TODO', 'IN_PROGRESS', @member_member, NULL, DATE_SUB(@now, INTERVAL 1 DAY)),
  (@hold_task, NULL, 'REQUESTED', @viewer_member, NULL, DATE_SUB(@now, INTERVAL 5 DAY)),
  (@hold_task, 'REQUESTED', 'TODO', @leader_member, NULL, DATE_SUB(@now, INTERVAL 4 DAY)),
  (@hold_task, 'TODO', 'IN_PROGRESS', @viewer_member, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
  (@hold_task, 'IN_PROGRESS', 'ON_HOLD', @viewer_member, '디자인 확인 회신 대기', DATE_SUB(@now, INTERVAL 6 HOUR)),
  (@completed_task, NULL, 'REQUESTED', @leader_member, NULL, DATE_SUB(@now, INTERVAL 7 DAY)),
  (@completed_task, 'REQUESTED', 'TODO', @leader_member, NULL, DATE_SUB(@now, INTERVAL 6 DAY)),
  (@completed_task, 'TODO', 'IN_PROGRESS', @leader_member, NULL, DATE_SUB(@now, INTERVAL 5 DAY)),
  (@completed_task, 'IN_PROGRESS', 'COMPLETED', @leader_member, NULL, DATE_SUB(@now, INTERVAL 2 DAY)),
  (@delayed_task, NULL, 'REQUESTED', @leader_member, NULL, DATE_SUB(@now, INTERVAL 6 DAY)),
  (@delayed_task, 'REQUESTED', 'TODO', @leader_member, NULL, DATE_SUB(@now, INTERVAL 5 DAY)),
  (@personal_task, NULL, 'TODO', @leader_personal_member, NULL, DATE_SUB(@now, INTERVAL 1 DAY));

INSERT INTO task_checklist_items (task_id, content, completed, completed_by_member_id,
                                  completed_at, sort_order, created_at, updated_at, version)
VALUES
  (@progress_task, '360px 모바일 레이아웃 확인', TRUE, @member_member,
   DATE_SUB(@now, INTERVAL 3 HOUR), 0, DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 3 HOUR), 1),
  (@progress_task, '키보드 포커스 순서 확인', TRUE, @member_member,
   DATE_SUB(@now, INTERVAL 2 HOUR), 1, DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 2 HOUR), 1),
  (@progress_task, '실기기 최종 확인', FALSE, NULL,
   NULL, 2, DATE_SUB(@now, INTERVAL 1 DAY), DATE_SUB(@now, INTERVAL 1 DAY), 0),
  (@delayed_task, '원인 정리', TRUE, @member_member,
   DATE_SUB(@now, INTERVAL 1 DAY), 0, DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 1 DAY), 1),
  (@delayed_task, '수정 일정 공유', FALSE, NULL,
   NULL, 1, DATE_SUB(@now, INTERVAL 2 DAY), DATE_SUB(@now, INTERVAL 2 DAY), 0);

INSERT INTO task_comments (task_id, author_member_id, content, created_at, updated_at, deleted_at, version)
VALUES
  (@progress_task, @leader_member, '모바일 점검 결과가 좋습니다. 남은 실기기 확인을 부탁해요.',
   DATE_SUB(@now, INTERVAL 90 MINUTE), NULL, NULL, 0),
  (@progress_task, @member_member, '네, 오늘 안에 마무리하고 공유하겠습니다.',
   DATE_SUB(@now, INTERVAL 60 MINUTE), NULL, NULL, 0),
  (@hold_task, @viewer_member, '디자인 회신이 오면 바로 재개하겠습니다.',
   DATE_SUB(@now, INTERVAL 5 HOUR), NULL, NULL, 0);

SET @mention_comment = (SELECT id FROM task_comments WHERE task_id = @progress_task AND author_member_id = @leader_member);
INSERT INTO comment_mentions (comment_id, mentioned_member_id, created_at)
VALUES (@mention_comment, @member_member, DATE_SUB(@now, INTERVAL 90 MINUTE));

INSERT INTO notifications (recipient_user_id, actor_user_id, group_id, task_id, comment_id,
                           type, event_key, title, message, read_at, created_at)
VALUES
  (@leader_user, @member_user, @team_group, @requested_task, NULL,
   'TASK_REQUESTED', CONCAT('DEMO:TASK_REQUESTED:', @requested_task),
   '새 업무 요청', '이팀원이 신규 기능 검토를 요청했습니다.', NULL, DATE_SUB(@now, INTERVAL 2 HOUR)),
  (@member_user, @leader_user, @team_group, @todo_task, NULL,
   'TASK_ASSIGNED', CONCAT('DEMO:TASK_ASSIGNED:', @todo_task),
   '업무 담당 지정', '발표 자료 초안 작성 업무가 배정되었습니다.', DATE_SUB(@now, INTERVAL 1 DAY),
   DATE_SUB(@now, INTERVAL 2 DAY)),
  (@member_user, @leader_user, @team_group, @progress_task, @mention_comment,
   'COMMENT_MENTIONED', CONCAT('DEMO:COMMENT_MENTIONED:', @mention_comment),
   '댓글에서 멘션됨', '김팀장이 모바일 화면 최종 점검 업무에서 멘션했습니다.', NULL,
   DATE_SUB(@now, INTERVAL 90 MINUTE)),
  (@viewer_user, @member_user, @team_group, @progress_task, NULL,
   'TASK_STATUS_CHANGED', CONCAT('DEMO:TASK_STATUS_CHANGED:', @progress_task),
   '업무 상태 변경', '모바일 화면 최종 점검 업무가 진행 중입니다.', NULL,
   DATE_SUB(@now, INTERVAL 1 DAY));

INSERT INTO calendar_events (group_id, created_by_member_id, type, title, description,
                             start_at, end_at, all_day, location, version, created_at, updated_at)
VALUES
  (@team_group, @leader_member, 'MEETING', '주간 진행 공유', '발표 전 진행 상황을 공유합니다.',
   DATE_ADD(@now, INTERVAL 1 DAY), DATE_ADD(DATE_ADD(@now, INTERVAL 1 DAY), INTERVAL 1 HOUR),
   FALSE, '회의실 A', 0, @now, @now),
  (@team_group, @leader_member, 'SCHEDULE', '발표 리허설', '핵심 사용자 흐름을 점검합니다.',
   DATE_ADD(@now, INTERVAL 3 DAY), DATE_ADD(DATE_ADD(@now, INTERVAL 3 DAY), INTERVAL 2 HOUR),
   FALSE, '온라인', 0, @now, @now),
  (@leader_personal_group, @leader_personal_member, 'TODO', '발표 장비 확인', NULL,
   DATE_ADD(@now, INTERVAL 4 DAY), DATE_ADD(DATE_ADD(@now, INTERVAL 4 DAY), INTERVAL 1 HOUR),
   FALSE, NULL, 0, @now, @now);

COMMIT;

SELECT username, nickname, status FROM users
WHERE username IN ('demo_leader', 'demo_member', 'demo_viewer') ORDER BY username;
SELECT type, name, timezone FROM work_groups
WHERE created_by IN (@leader_user, @member_user, @viewer_user) ORDER BY type, name;
SELECT status, COUNT(*) AS task_count FROM tasks
WHERE group_id IN (@team_group, @leader_personal_group) GROUP BY status ORDER BY status;
