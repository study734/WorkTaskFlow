import {
  ActionNarrativeDraftItem,
  DecisionNarrativeDraftItem,
  LocalReference,
  NarrativeDraft,
  NarrativeDraftItem,
  RiskNarrativeDraftItem,
} from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';

export function AiReportDraftEditor({ value, members, disabled, onChange, onSave, onCancel }: {
  value: NarrativeDraft;
  members: LocalReference[];
  disabled: boolean;
  onChange: (value: NarrativeDraft) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  const { t } = useLanguage();
  const changeItem = (
    key: 'changes' | 'achievements' | 'limitations',
    index: number,
    textTemplate: string,
  ) => onChange({
    ...value,
    [key]: value[key].map((item, itemIndex) =>
      itemIndex === index ? { ...item, textTemplate } : item),
  });

  return <section className="ai-report-editor">
    <div className="ai-report-editor-heading">
      <div><strong>{t('리포트 초안 편집', 'Edit report draft')}</strong>
        <p>{t(
          '근거와 업무 연결은 유지됩니다. 수치와 날짜는 {{근거 키}} 형식만 사용할 수 있습니다.',
          'Evidence and task links stay fixed. Numbers and dates must use {{evidence.key}} placeholders.',
        )}</p></div>
    </div>
    <label><span>{t('제목', 'Headline')}</span>
      <input maxLength={120} value={value.headlineTemplate}
        onChange={(event) => onChange({ ...value, headlineTemplate: event.target.value })} /></label>
    <DraftItemField label={t('핵심 요약', 'Summary')} item={value.summary}
      onChange={(textTemplate) =>
        onChange({ ...value, summary: { ...value.summary, textTemplate } })} />
    <DraftItemList title={t('변화', 'Changes')} items={value.changes}
      onChange={(index, text) => changeItem('changes', index, text)} />
    <DraftItemList title={t('성과', 'Achievements')} items={value.achievements}
      onChange={(index, text) => changeItem('achievements', index, text)} />
    <RiskFields items={value.risks}
      onChange={(risks) => onChange({ ...value, risks })} />
      <ActionFields items={value.topActions}
        members={members}
        onChange={(topActions) => onChange({ ...value, topActions })} />
    <DecisionFields items={value.leaderDecisions}
      onChange={(leaderDecisions) => onChange({ ...value, leaderDecisions })} />
    <DraftItemList title={t('데이터 제한', 'Limitations')} items={value.limitations}
      onChange={(index, text) => changeItem('limitations', index, text)} />
    <div className="ai-report-editor-actions">
      <button type="button" className="secondary" disabled={disabled} onClick={onCancel}>
        {t('취소', 'Cancel')}
      </button>
      <button type="button" className="primary" disabled={disabled} onClick={onSave}>
        {disabled ? t('저장 중...', 'Saving...') : t('초안 저장', 'Save draft')}
      </button>
    </div>
  </section>;
}

function DraftItemList({ title, items, onChange }: {
  title: string;
  items: NarrativeDraftItem[];
  onChange: (index: number, text: string) => void;
}) {
  if (items.length === 0) return null;
  return <fieldset><legend>{title}</legend>{items.map((item, index) =>
    <DraftItemField key={index} label={`${title} ${index + 1}`} item={item}
      onChange={(text) => onChange(index, text)} />)}</fieldset>;
}

function DraftItemField({ label, item, onChange }: {
  label: string;
  item: NarrativeDraftItem;
  onChange: (text: string) => void;
}) {
  return <label><span>{label}</span>
    <textarea maxLength={800} value={item.textTemplate}
      onChange={(event) => onChange(event.target.value)} />
    <EvidenceKeys value={item.evidenceKeys} />
  </label>;
}

function RiskFields({ items, onChange }: {
  items: RiskNarrativeDraftItem[];
  onChange: (items: RiskNarrativeDraftItem[]) => void;
}) {
  const { t } = useLanguage();
  if (items.length === 0) return null;
  return <fieldset><legend>{t('위험', 'Risks')}</legend>{items.map((item, index) =>
    <label key={index}><span>{item.severity} · {t('위험', 'Risk')} {index + 1}</span>
      <textarea maxLength={600} value={item.textTemplate}
        onChange={(event) => onChange(items.map((value, itemIndex) =>
          itemIndex === index ? { ...value, textTemplate: event.target.value } : value))} />
      <EvidenceKeys value={item.evidenceKeys} />
    </label>)}</fieldset>;
}

function ActionFields({ items, members, onChange }: {
  items: ActionNarrativeDraftItem[];
  members: LocalReference[];
  onChange: (items: ActionNarrativeDraftItem[]) => void;
}) {
  const { t } = useLanguage();
  return <fieldset><legend>{t('우선 행동', 'Top actions')}</legend>{items.map((item, index) =>
    <div className="ai-report-editor-pair" key={item.priority}>
      <label><span>P{item.priority} · {t('행동', 'Action')}</span>
        <textarea maxLength={500} value={item.actionTemplate}
          onChange={(event) => onChange(items.map((value, itemIndex) =>
            itemIndex === index ? { ...value, actionTemplate: event.target.value } : value))} />
      </label>
        <label><span>{t('이유', 'Reason')}</span>
          <textarea maxLength={500} value={item.reasonTemplate}
            onChange={(event) => onChange(items.map((value, itemIndex) =>
              itemIndex === index ? { ...value, reasonTemplate: event.target.value } : value))} />
        </label>
        <label><span>{t('담당', 'Owner')}</span>
          <select required value={item.ownerRef ?? ''}
            onChange={(event) => onChange(items.map((value, itemIndex) =>
              itemIndex === index ? { ...value, ownerRef: event.target.value } : value))}>
            <option value="" disabled>{t('담당 선택', 'Select owner')}</option>
            {members.map((member) =>
              <option key={member.ref} value={member.ref}>{member.label}</option>)}
          </select>
        </label>
        <EvidenceKeys value={item.evidenceKeys} />
    </div>)}</fieldset>;
}

function DecisionFields({ items, onChange }: {
  items: DecisionNarrativeDraftItem[];
  onChange: (items: DecisionNarrativeDraftItem[]) => void;
}) {
  const { t } = useLanguage();
  if (items.length === 0) return null;
  return <fieldset><legend>{t('팀장 결정사항', 'Leader decisions')}</legend>
    {items.map((item, index) => <div className="ai-report-editor-pair" key={index}>
      <label><span>{t('결정 질문', 'Decision question')}</span>
        <textarea maxLength={500} value={item.questionTemplate}
          onChange={(event) => onChange(items.map((value, itemIndex) =>
            itemIndex === index ? { ...value, questionTemplate: event.target.value } : value))} />
      </label>
      <label><span>{t('영향', 'Impact')}</span>
        <textarea maxLength={500} value={item.impactTemplate}
          onChange={(event) => onChange(items.map((value, itemIndex) =>
            itemIndex === index ? { ...value, impactTemplate: event.target.value } : value))} />
      </label>
      <EvidenceKeys value={item.evidenceKeys} />
    </div>)}
  </fieldset>;
}

function EvidenceKeys({ value }: { value: string[] }) {
  return <small className="ai-report-editor-evidence">{value.join(' · ')}</small>;
}
