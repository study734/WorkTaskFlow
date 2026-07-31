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
