UPDATE work_groups
SET name = CONCAT(LEFT(name, CHAR_LENGTH(name) - CHAR_LENGTH('개인 공간')), '개인 일정'),
    updated_at = CURRENT_TIMESTAMP
WHERE type = 'PERSONAL'
  AND name LIKE '%의 개인 공간';
