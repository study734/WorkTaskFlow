import { KeyboardEvent } from 'react';
import { useLanguage } from '../../../app/LanguageContext';

/** 서버의 CreateTaskRequest.checklistItems 상한과 같아야 한다. */
export const checklistDraftLimit = 30;

export function cleanChecklistDraft(items: string[]) {
  return items.map((item) => item.trim()).filter((item) => item.length > 0);
}

export function ChecklistDraftField({ items, onChange, disabled }: {
  items: string[];
  onChange: (items: string[]) => void;
  disabled?: boolean;
}) {
  const { t } = useLanguage();
  const add = () => { if (items.length < checklistDraftLimit) onChange([...items, '']); };
  const update = (index: number, value: string) =>
    onChange(items.map((item, current) => current === index ? value : item));
  const remove = (index: number) => onChange(items.filter((_, current) => current !== index));

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>, index: number) {
    // 항목을 입력하다 누른 Enter가 업무 등록으로 새지 않도록 다음 줄을 연다.
    if (event.key !== 'Enter') return;
    event.preventDefault();
    if (index === items.length - 1) add();
  }

  return <div className="field checklist-draft">
    <span>{t('체크리스트 (선택)', 'Checklist (optional)')}</span>
    {items.length > 0 && <ul className="checklist-draft-list">{items.map((item, index) =>
      <li key={index}>
        <input
          value={item}
          maxLength={300}
          disabled={disabled}
          data-task-paste="checklist"
          data-checklist-index={index}
          autoFocus={item === '' && index === items.length - 1}
          aria-label={t(`체크리스트 ${index + 1}번 항목`, `Checklist item ${index + 1}`)}
          placeholder={t('예: 참고 자료 정리', 'e.g. Gather reference material')}
          onChange={(event) => update(index, event.target.value)}
          onKeyDown={(event) => onKeyDown(event, index)}
        />
        <button
          type="button"
          className="checklist-draft-remove"
          disabled={disabled}
          aria-label={t(`체크리스트 ${index + 1}번 항목 삭제`, `Remove checklist item ${index + 1}`)}
          onClick={() => remove(index)}
        >×</button>
      </li>)}</ul>}
    <button className="secondary checklist-draft-add" type="button"
      disabled={disabled || items.length >= checklistDraftLimit} onClick={add}>
      <span aria-hidden="true">＋</span> {t('체크리스트 항목 추가', 'Add checklist item')}
    </button>
    <small className="field-help">{t(
      `업무를 만들 때 해야 할 일을 미리 나눠 둘 수 있습니다. 최대 ${checklistDraftLimit}개이며, 비워 둔 줄은 저장되지 않습니다.`,
      `Break the task into steps up front. Up to ${checklistDraftLimit} items; blank lines are skipped.`,
    )}</small>
  </div>;
}
