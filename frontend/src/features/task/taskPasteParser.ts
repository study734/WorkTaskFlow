/** 붙여넣은 여러 줄 텍스트를 업무 제목·체크리스트로 나누는 순수 함수 모음. */

/** `- 항목` `* 항목` `• 항목` `☐ 항목` `1. 항목` `1) 항목` 앞머리를 지운다. */
const listMarker = /^(?:[-*•‣▪·☐☑]\s*|\d{1,3}[.)]\s+)/;

/** 앞뒤 공백과 목록 기호를 지운 줄들만 남긴다. 빈 줄은 버린다. */
export function toPastedLines(text: string): string[] {
  return text
    .split(/\r\n|\r|\n/)
    .map((line) => line.trim().replace(listMarker, '').trim())
    .filter((line) => line.length > 0);
}

/** 붙여넣은 텍스트가 제목 한 줄만인지(=기존 붙여넣기 동작을 그대로 둘지) 판단한다. */
export function isSingleLinePaste(text: string): boolean {
  return toPastedLines(text).length <= 1;
}

export type ChecklistMergeResult = {
  /** 기존 항목을 유지한 채 새 항목을 넣은 결과. 상한을 넘는 줄은 들어 있지 않다. */
  items: string[];
  /** 실제로 추가된 개수. */
  added: number;
  /** 이미 같은 항목이 있어 건너뛴 개수. */
  duplicates: number;
  /** 상한을 넘어 넣지 못한 개수. */
  overLimit: number;
};

/**
 * 기존 체크리스트를 유지한 채 새 줄을 넣는다.
 * 이미 같은 내용이 있으면 건너뛰고, 상한을 넘는 줄도 넣지 않는다.
 * `insertAt`을 주지 않으면 목록 끝에 붙인다.
 */
export function mergeChecklistItems(
  existing: string[], lines: string[], limit: number, insertAt?: number,
): ChecklistMergeResult {
  const kept = [...existing];
  // 목록 끝에 붙일 때는, 사용자가 열어 둔 빈 줄이 항목 사이에 끼지 않도록 꼬리의 빈 줄만 정리한다.
  if (insertAt === undefined) while (kept.length > 0 && kept[kept.length - 1].trim() === '') kept.pop();
  const seen = new Set(kept.map((item) => item.trim()).filter((item) => item.length > 0));
  const accepted: string[] = [];
  let duplicates = 0;
  let overLimit = 0;
  for (const line of lines) {
    if (seen.has(line)) { duplicates += 1; continue; }
    if (kept.length + accepted.length >= limit) { overLimit += 1; continue; }
    accepted.push(line);
    seen.add(line);
  }
  kept.splice(insertAt ?? kept.length, 0, ...accepted);
  return { items: kept, added: accepted.length, duplicates, overLimit };
}

export type TaskPasteSplit = {
  /** 제목으로 쓸 첫 줄. 제목을 채우지 않는 경우 undefined. */
  title?: string;
  /** 체크리스트로 보낼 나머지 줄. */
  checklistLines: string[];
};

/**
 * 붙여넣은 텍스트를 제목과 체크리스트로 나눈다.
 * 제목이 이미 있으면 덮어쓰지 않고 모든 줄을 체크리스트로 보낸다.
 */
export function splitPastedTask(text: string, hasTitle: boolean): TaskPasteSplit {
  const lines = toPastedLines(text);
  if (hasTitle) return { checklistLines: lines };
  const [first, ...rest] = lines;
  return first === undefined ? { checklistLines: [] } : { title: first, checklistLines: rest };
}
