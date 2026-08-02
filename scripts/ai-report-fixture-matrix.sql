-- AI 리포트 품질 확인용 상황 fixture.
--
-- 지금까지 시연 데이터가 그룹 하나뿐이라 "위험이 없는 기간", "업무가 100건을 넘는 기간"
-- 같은 상황의 문서를 실물로 볼 수 없었다. 상황마다 그룹을 따로 두면 기간을 바꾸지 않고
-- 같은 주간으로 여섯 장을 나란히 뽑아 비교할 수 있다.
--
-- 로컬 개발 DB 전용이다. 운영에서 실행하지 않는다.
-- 다시 실행해도 같은 상태가 되도록 이름으로 지우고 새로 만든다.
--
--   docker exec -i worktaskflow-mysql-1 mysql -uroot -p<암호> teamProject < scripts/ai-report-fixture-matrix.sql
--
-- 기간은 2026-07-20 ~ 2026-07-27(월~일)로 맞춘다. 이미 끝난 주간이라 AI 리포트를 만들 수 있다.

SET @period_start = '2026-07-20 00:00:00';
SET @period_end   = '2026-07-26 18:00:00';
SET @leader_user  = 1;   -- devuser
SET @member_a     = 3;   -- kim_pm
SET @member_b     = 4;   -- lee_dev

-- ---------- 이전 실행분 정리 ----------

DELETE te FROM task_activity_events te
    JOIN tasks t ON t.id = te.task_id
    JOIN work_groups g ON g.id = t.group_id
    WHERE g.name LIKE 'FIXTURE %';
DELETE t FROM tasks t JOIN work_groups g ON g.id = t.group_id WHERE g.name LIKE 'FIXTURE %';
DELETE gm FROM group_members gm JOIN work_groups g ON g.id = gm.group_id WHERE g.name LIKE 'FIXTURE %';
DELETE FROM work_groups WHERE name LIKE 'FIXTURE %';

-- ---------- 그룹 ----------
-- AI 리포트는 유료 팀 그룹에서만 열린다. 여섯 개 모두 TEAM·PAID로 만든다.

INSERT INTO work_groups (type, name, description, timezone, dashboard_visibility,
                         created_by, created_at, updated_at, membership_plan)
VALUES
    ('TEAM', 'FIXTURE 1 위험 없음',   '완료만 있고 지연·미지정·승인 대기가 없다', 'Asia/Seoul', 'MEMBERS', @leader_user, @period_start, @period_start, 'PAID'),
    ('TEAM', 'FIXTURE 2 지연 다수',   '마감이 지난 진행 업무가 여럿이다',         'Asia/Seoul', 'MEMBERS', @leader_user, @period_start, @period_start, 'PAID'),
    ('TEAM', 'FIXTURE 3 승인 대기',   'REQUESTED 상태가 쌓여 있다',               'Asia/Seoul', 'MEMBERS', @leader_user, @period_start, @period_start, 'PAID'),
    ('TEAM', 'FIXTURE 4 부하 편중',   '한 사람에게 활성 업무가 몰려 있다',        'Asia/Seoul', 'MEMBERS', @leader_user, @period_start, @period_start, 'PAID'),
    ('TEAM', 'FIXTURE 5 대량 업무',   '업무가 배열 상한 100건을 넘는다',          'Asia/Seoul', 'MEMBERS', @leader_user, @period_start, @period_start, 'PAID'),
    ('TEAM', 'FIXTURE 6 업무 없음',   '기간에 업무가 하나도 없다',                'Asia/Seoul', 'MEMBERS', @leader_user, @period_start, @period_start, 'PAID');

-- 리더는 모든 fixture에서 devuser다. 팀원 둘은 부하 편중을 만들 때 쓴다.
INSERT INTO group_members (group_id, user_id, role, status, joined_at)
SELECT g.id, u.user_id, u.role, 'ACTIVE', @period_start
FROM work_groups g
CROSS JOIN (SELECT @leader_user AS user_id, 'LEADER' AS role
            UNION ALL SELECT @member_a, 'MEMBER'
            UNION ALL SELECT @member_b, 'MEMBER') u
WHERE g.name LIKE 'FIXTURE %';

-- ---------- 업무 ----------
-- 담당자는 group_members.id를 가리킨다. 그룹마다 새로 만들어졌으므로 조회해서 쓴다.

-- 1. 위험 없음: 전부 완료, 기한 내
INSERT INTO tasks (group_id, requester_member_id, assignee_member_id, title, priority, status,
                   due_at, completed_at, created_at, updated_at, version)
SELECT g.id, ldr.id, ldr.id, CONCAT('완료된 업무 ', n.i), 'NORMAL', 'COMPLETED',
       DATE_ADD(@period_start, INTERVAL 3 DAY), DATE_ADD(@period_start, INTERVAL 2 DAY),
       @period_start, @period_start, 0
FROM work_groups g
JOIN group_members ldr ON ldr.group_id = g.id AND ldr.role = 'LEADER'
JOIN (SELECT 1 i UNION SELECT 2 UNION SELECT 3 UNION SELECT 4) n
WHERE g.name = 'FIXTURE 1 위험 없음';

-- 2. 지연 다수: 마감이 지났는데 아직 진행 중
INSERT INTO tasks (group_id, requester_member_id, assignee_member_id, title, priority, status,
                   due_at, created_at, updated_at, version)
SELECT g.id, ldr.id, ldr.id, CONCAT('마감이 지난 업무 ', n.i), 'HIGH', 'IN_PROGRESS',
       DATE_ADD(@period_start, INTERVAL 1 DAY), @period_start, @period_start, 0
FROM work_groups g
JOIN group_members ldr ON ldr.group_id = g.id AND ldr.role = 'LEADER'
JOIN (SELECT 1 i UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) n
WHERE g.name = 'FIXTURE 2 지연 다수';

-- 3. 승인 대기: 담당자 없이 요청만 쌓임
INSERT INTO tasks (group_id, requester_member_id, title, priority, status,
                   due_at, created_at, updated_at, version)
SELECT g.id, ldr.id, CONCAT('승인을 기다리는 업무 ', n.i), 'NORMAL', 'REQUESTED',
       DATE_ADD(@period_end, INTERVAL 5 DAY), @period_start, @period_start, 0
FROM work_groups g
JOIN group_members ldr ON ldr.group_id = g.id AND ldr.role = 'LEADER'
JOIN (SELECT 1 i UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) n
WHERE g.name = 'FIXTURE 3 승인 대기';

-- 4. 부하 편중: 한 팀원이 활성 업무를 독차지
INSERT INTO tasks (group_id, requester_member_id, assignee_member_id, title, priority, status,
                   due_at, created_at, updated_at, version)
SELECT g.id, ldr.id, busy.id, CONCAT('한 사람에게 몰린 업무 ', n.i), 'NORMAL', 'IN_PROGRESS',
       DATE_ADD(@period_end, INTERVAL 2 DAY), @period_start, @period_start, 0
FROM work_groups g
JOIN group_members ldr ON ldr.group_id = g.id AND ldr.role = 'LEADER'
JOIN group_members busy ON busy.group_id = g.id AND busy.user_id = @member_a
JOIN (SELECT 1 i UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
      UNION SELECT 6 UNION SELECT 7 UNION SELECT 8) n
WHERE g.name = 'FIXTURE 4 부하 편중';

-- 다른 팀원에게는 한 건만 준다. 편중이 드러나려면 비교 대상이 있어야 한다.
INSERT INTO tasks (group_id, requester_member_id, assignee_member_id, title, priority, status,
                   due_at, created_at, updated_at, version)
SELECT g.id, ldr.id, quiet.id, '다른 팀원의 업무', 'NORMAL', 'IN_PROGRESS',
       DATE_ADD(@period_end, INTERVAL 2 DAY), @period_start, @period_start, 0
FROM work_groups g
JOIN group_members ldr ON ldr.group_id = g.id AND ldr.role = 'LEADER'
JOIN group_members quiet ON quiet.group_id = g.id AND quiet.user_id = @member_b
WHERE g.name = 'FIXTURE 4 부하 편중';

-- 5. 대량 업무: 105건. Snapshot 배열 상한(100)을 넘겨 집계 모수와 잘림 공개를 확인한다.
INSERT INTO tasks (group_id, requester_member_id, assignee_member_id, title, priority, status,
                   due_at, completed_at, created_at, updated_at, version)
SELECT g.id, ldr.id, ldr.id, CONCAT('대량 업무 ', seq.n), 'NORMAL',
       IF(seq.n % 3 = 0, 'COMPLETED', 'IN_PROGRESS'),
       DATE_ADD(@period_start, INTERVAL 4 DAY),
       IF(seq.n % 3 = 0, DATE_ADD(@period_start, INTERVAL 2 DAY), NULL),
       @period_start, @period_start, 0
FROM work_groups g
JOIN group_members ldr ON ldr.group_id = g.id AND ldr.role = 'LEADER'
JOIN (SELECT (a.i + b.i * 10 + c.i * 100) + 1 AS n
      FROM (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
            UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
      CROSS JOIN (SELECT 0 i UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
            UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
      CROSS JOIN (SELECT 0 i UNION SELECT 1) c
      HAVING n <= 105) seq
WHERE g.name = 'FIXTURE 5 대량 업무';

-- 6. 업무 없음: 아무것도 넣지 않는다.

SELECT g.name, COUNT(t.id) AS tasks
FROM work_groups g
LEFT JOIN tasks t ON t.group_id = g.id
WHERE g.name LIKE 'FIXTURE %'
GROUP BY g.id, g.name
ORDER BY g.name;
