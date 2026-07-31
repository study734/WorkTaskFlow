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
