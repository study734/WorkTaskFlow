import assert from 'node:assert/strict';
import test from 'node:test';
import { isSingleLinePaste, mergeChecklistItems, splitPastedTask, toPastedLines } from '../src/features/task/taskPasteParser.ts';

const limit = 30;

test('첫 줄은 제목, 나머지 줄은 체크리스트가 된다', () => {
  const result = splitPastedTask('발표 자료 초안 작성\n- 초안 자료 조사하기\n- 자료 검토하기', false);
  assert.equal(result.title, '발표 자료 초안 작성');
  assert.deepEqual(result.checklistLines, ['초안 자료 조사하기', '자료 검토하기']);
});

test('제목이 이미 있으면 덮어쓰지 않고 모든 줄을 체크리스트로 보낸다', () => {
  const result = splitPastedTask('발표 자료 초안 작성\n- 자료 검토하기', true);
  assert.equal(result.title, undefined);
  assert.deepEqual(result.checklistLines, ['발표 자료 초안 작성', '자료 검토하기']);
});

test('목록 기호를 모두 제거한다', () => {
  assert.deepEqual(
    toPastedLines('- 항목\n* 항목2\n• 항목3\n1. 항목4\n2) 항목5\n☐ 항목6'),
    ['항목', '항목2', '항목3', '항목4', '항목5', '항목6'],
  );
});

test('빈 줄과 앞뒤 공백을 제거한다', () => {
  assert.deepEqual(toPastedLines('  제목  \n\n   \n\t- 항목  \n'), ['제목', '항목']);
});

test('목록 기호가 아닌 숫자는 남긴다', () => {
  assert.deepEqual(toPastedLines('3.5시간 검토'), ['3.5시간 검토']);
});

test('한 줄 붙여넣기를 구분한다', () => {
  assert.equal(isSingleLinePaste('발표 자료 초안 작성'), true);
  assert.equal(isSingleLinePaste('발표 자료 초안 작성\n- 자료 검토하기'), false);
  assert.equal(isSingleLinePaste('발표 자료 초안 작성\n\n'), true);
});

test('기존 항목을 유지한 채 뒤에 붙이고 중복은 건너뛴다', () => {
  const merged = mergeChecklistItems(['자료 검토하기'], ['초안 자료 조사하기', '자료 검토하기'], limit);
  assert.deepEqual(merged.items, ['자료 검토하기', '초안 자료 조사하기']);
  assert.equal(merged.added, 1);
  assert.equal(merged.duplicates, 1);
  assert.equal(merged.overLimit, 0);
});

test('꼬리의 빈 줄만 정리하고 사이의 입력은 유지한다', () => {
  const merged = mergeChecklistItems(['조사하기', ''], ['검토하기'], limit);
  assert.deepEqual(merged.items, ['조사하기', '검토하기']);
});

test('상한을 넘는 항목은 추가하지 않고 개수를 알려 준다', () => {
  const existing = Array.from({ length: 28 }, (_, index) => `기존 ${index}`);
  const merged = mergeChecklistItems(existing, ['새 1', '새 2', '새 3', '새 4'], limit);
  assert.equal(merged.items.length, limit);
  assert.equal(merged.added, 2);
  assert.equal(merged.overLimit, 2);
});

test('지정한 위치 뒤에 새 항목을 넣는다', () => {
  const merged = mergeChecklistItems(['첫째', '둘째'], ['사이'], limit, 1);
  assert.deepEqual(merged.items, ['첫째', '사이', '둘째']);
});
