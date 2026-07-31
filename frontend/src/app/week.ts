export function lastCompletedWeekStart(timeZone?: string) {
  const value = zonedToday(timeZone);
  value.setDate(value.getDate() - ((value.getDay() + 6) % 7) - 7);
  return formatLocalDate(value);
}

export function currentWeekStart(timeZone?: string) {
  const value = zonedToday(timeZone);
  value.setDate(value.getDate() - ((value.getDay() + 6) % 7));
  return formatLocalDate(value);
}

/**
 * `YYYY-MM-DD` 문자열끼리만 계산한다. `new Date('2026-07-27')`는 UTC 자정으로 읽히기
 * 때문에, 지역 시간대 getter로 다시 꺼내면 날짜가 하루 밀린다.
 */
export function addDays(date: string, days: number) {
  const [year, month, day] = date.split('-').map(Number);
  if (!year || !month || !day) return date;
  const value = new Date(Date.UTC(year, month - 1, day + days));
  return `${value.getUTCFullYear()}-${String(value.getUTCMonth() + 1).padStart(2, '0')}-${String(value.getUTCDate()).padStart(2, '0')}`;
}

/**
 * 그 날짜가 속한 주의 월요일. AI 주간 리포트는 월요일 시작 7일만 받는데, 화면의 주차
 * 선택은 달의 1~7일·8~14일 같은 날짜 묶음이라 월요일이 아닐 수 있다. 고른 주간을 다른
 * 주로 옮기지 않고, 그 날이 들어 있는 주의 시작일만 구한다.
 */
export function mondayOf(date: string) {
  const [year, month, day] = date.split('-').map(Number);
  if (!year || !month || !day) return date;
  const value = new Date(Date.UTC(year, month - 1, day));
  return addDays(date, -((value.getUTCDay() + 6) % 7));
}

/** 주간 API는 종료일을 배타적으로 받는다. 화면·PDF는 포함 종료일로 보여 준다. */
export function weekToExclusive(from: string) {
  return addDays(from, 7);
}

export function weekEndInclusive(from: string) {
  return addDays(from, 6);
}

export function zonedTodayString(timeZone?: string) {
  return formatLocalDate(zonedToday(timeZone));
}

/** 종료일이 오늘보다 뒤면 아직 끝나지 않은 주간이다. */
export function isCompletedWeek(from: string, timeZone?: string) {
  return weekToExclusive(from) <= zonedTodayString(timeZone);
}

function zonedToday(timeZone?: string) {
  const now = new Date();
  if (!timeZone) return now;

  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
  }).formatToParts(now);
  const number = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((part) => part.type === type)?.value);
  return new Date(number('year'), number('month') - 1, number('day'));
}

function formatLocalDate(value: Date) {
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}
