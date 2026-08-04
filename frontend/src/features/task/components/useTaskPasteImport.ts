import { useEffect, useRef, useState } from 'react';
import { useLanguage } from '../../../app/LanguageContext';
import { mergeChecklistItems, splitPastedTask, toPastedLines } from '../taskPasteParser';
import { checklistDraftLimit } from './ChecklistDraftField';

const titleMaxLength = 120;
const checklistItemMaxLength = 300;

/** 붙여넣기를 받을 입력란은 `data-task-paste` 값으로 구분한다. */
type PasteField = 'title' | 'checklist' | 'description';

type Options = {
  /** 새 업무 만들기 모달이 열려 있는 동안에만 true. 닫히면 문서 붙여넣기를 건드리지 않는다. */
  active: boolean;
  title: string;
  setTitle: (value: string) => void;
  checklistItems: string[];
  setChecklistItems: (items: string[]) => void;
  disabled?: boolean;
};

/**
 * 새 업무 만들기 모달에서 여러 줄 붙여넣기를 제목 한 줄 + 체크리스트로 나눠 넣는다.
 * 반환값은 사용자에게 보여 줄 결과 안내 문구다.
 */
export function useTaskPasteImport(options: Options) {
  const { t } = useLanguage();
  const [notice, setNotice] = useState('');
  const optionsRef = useRef(options);
  optionsRef.current = options;
  const translate = useRef(t);
  translate.current = t;

  useEffect(() => { if (!options.active) setNotice(''); }, [options.active]);

  useEffect(() => {
    if (!options.active) return;
    function onPaste(event: ClipboardEvent) {
      const state = optionsRef.current;
      if (!state.active || state.disabled) return;
      const text = event.clipboardData?.getData('text/plain') ?? '';
      // 한 줄 붙여넣기는 어느 입력란이든 기존 동작 그대로 둔다.
      if (!/[\r\n]/.test(text)) return;
      const target = event.target instanceof HTMLElement ? event.target : null;
      const field = target?.closest<HTMLElement>('[data-task-paste]')?.dataset.taskPaste as PasteField | undefined;
      // 설명 입력란과 우리가 다루지 않는 입력란은 브라우저 기본 붙여넣기를 유지한다.
      if (field === 'description') return;
      if (!field && isEditable(target)) return;

      const lines = toPastedLines(text);
      if (lines.length === 0) return;
      if (field === 'checklist') {
        applyChecklistPaste(event, target, lines, state, setNotice, translate.current);
        return;
      }
      if (field === 'title') {
        applyTitlePaste(event, target, lines, state, setNotice, translate.current);
        return;
      }
      applyAreaPaste(event, text, state, setNotice, translate.current);
    }
    document.addEventListener('paste', onPaste);
    return () => document.removeEventListener('paste', onPaste);
  }, [options.active]);

  return notice;
}

function isEditable(target: HTMLElement | null) {
  return target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement
    || target instanceof HTMLSelectElement || target?.isContentEditable === true;
}

/** 제목 입력란: 첫 줄은 커서 위치에 넣고 나머지는 체크리스트로 보낸다. */
function applyTitlePaste(
  event: ClipboardEvent, target: HTMLElement | null, lines: string[],
  state: Options, setNotice: (value: string) => void, t: (ko: string, en: string) => string,
) {
  if (lines.length <= 1) return; // 한 줄이면 제목에만 넣는 기존 동작을 유지한다.
  event.preventDefault();
  const input = target instanceof HTMLInputElement ? target : null;
  const current = input?.value ?? state.title;
  const start = input?.selectionStart ?? current.length;
  const end = input?.selectionEnd ?? start;
  const nextTitle = (current.slice(0, start) + lines[0] + current.slice(end)).slice(0, titleMaxLength);
  state.setTitle(nextTitle);
  const merged = mergeChecklistItems(state.checklistItems, limitLines(lines.slice(1)), checklistDraftLimit);
  state.setChecklistItems(merged.items);
  setNotice(noticeText(t, true, merged.added, merged.duplicates, merged.overLimit));
}

/** 체크리스트 입력란: 첫 줄은 그 칸에 넣고 나머지는 바로 아래에 새 항목으로 추가한다. */
function applyChecklistPaste(
  event: ClipboardEvent, target: HTMLElement | null, lines: string[],
  state: Options, setNotice: (value: string) => void, t: (ko: string, en: string) => string,
) {
  event.preventDefault();
  const input = target instanceof HTMLInputElement ? target : null;
  const index = Number(input?.dataset.checklistIndex ?? -1);
  if (!input || !Number.isInteger(index) || index < 0 || index >= state.checklistItems.length) {
    const merged = mergeChecklistItems(state.checklistItems, limitLines(lines), checklistDraftLimit);
    state.setChecklistItems(merged.items);
    setNotice(noticeText(t, false, merged.added, merged.duplicates, merged.overLimit));
    return;
  }
  const start = input.selectionStart ?? input.value.length;
  const end = input.selectionEnd ?? start;
  const filled = (input.value.slice(0, start) + lines[0] + input.value.slice(end)).slice(0, checklistItemMaxLength);
  // 비어 있던 칸을 첫 줄로 채웠다면 그 칸도 이번에 입력한 항목으로 센다.
  const filledEmpty = input.value.trim() === '' ? 1 : 0;
  const withFirst = state.checklistItems.map((item, current) => current === index ? filled : item);
  const merged = mergeChecklistItems(withFirst, limitLines(lines.slice(1)), checklistDraftLimit, index + 1);
  state.setChecklistItems(merged.items);
  setNotice(noticeText(t, false, merged.added + filledEmpty, merged.duplicates, merged.overLimit));
}

/** 모달의 빈 영역: 제목이 비어 있을 때만 첫 줄을 제목으로 쓴다. */
function applyAreaPaste(
  event: ClipboardEvent, text: string,
  state: Options, setNotice: (value: string) => void, t: (ko: string, en: string) => string,
) {
  event.preventDefault();
  const split = splitPastedTask(text, state.title.trim().length > 0);
  if (split.title !== undefined) state.setTitle(split.title.slice(0, titleMaxLength));
  const merged = mergeChecklistItems(state.checklistItems, limitLines(split.checklistLines), checklistDraftLimit);
  state.setChecklistItems(merged.items);
  setNotice(noticeText(t, split.title !== undefined, merged.added, merged.duplicates, merged.overLimit));
}

function limitLines(lines: string[]) {
  return lines.map((line) => line.slice(0, checklistItemMaxLength));
}

function noticeText(
  t: (ko: string, en: string) => string, titleSet: boolean, added: number, duplicates: number, overLimit: number,
) {
  const parts: string[] = [];
  if (titleSet && added > 0) parts.push(t(`제목 1개와 체크리스트 ${added}개를 입력했습니다.`, `Added 1 title and ${added} checklist items.`));
  else if (titleSet) parts.push(t('제목 1개를 입력했습니다.', 'Added 1 title.'));
  else if (added > 0) parts.push(t(`체크리스트 ${added}개를 입력했습니다.`, `Added ${added} checklist items.`));
  else parts.push(t('추가된 항목이 없습니다.', 'Nothing was added.'));
  if (duplicates > 0) parts.push(t(`이미 있는 항목 ${duplicates}개는 건너뛰었습니다.`, `Skipped ${duplicates} duplicate items.`));
  if (overLimit > 0) parts.push(t(
    `체크리스트는 최대 ${checklistDraftLimit}개까지여서 ${overLimit}개는 추가하지 않았습니다.`,
    `The checklist holds up to ${checklistDraftLimit} items, so ${overLimit} were not added.`,
  ));
  return parts.join(' ');
}
