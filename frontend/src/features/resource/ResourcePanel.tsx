import { FormEvent, useEffect, useId, useState } from 'react';
import { errorMessage } from '../../api/client';
import { GroupResource, resourceApi } from '../../api/resourceApi';
import { useLanguage } from '../../app/LanguageContext';

export function ResourcePanel({ groupId, taskId }: { groupId: number; taskId?: number }) {
  const { t, language } = useLanguage();
  const [items, setItems] = useState<GroupResource[]>([]);
  const [mode, setMode] = useState<'LINK' | 'FILE'>('LINK');
  const [title, setTitle] = useState('');
  const [url, setUrl] = useState('');
  const [file, setFile] = useState<File>();
  const [fileInputKey, setFileInputKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const fileInputId = useId();
  const load = () => (taskId ? resourceApi.taskList(taskId) : resourceApi.groupList(groupId)).then(setItems);
  useEffect(() => {
    setLoading(true); setError('');
    load().catch((value) => setError(errorMessage(value))).finally(() => setLoading(false));
  }, [groupId, taskId]);
  async function submit(event: FormEvent) {
    event.preventDefault(); setPending(true); setError('');
    try {
      if (mode === 'LINK') await resourceApi.addLink(groupId, title, url, taskId);
      else if (file) await resourceApi.upload(groupId, file, title, taskId);
      setTitle(''); setUrl(''); setFile(undefined); setFileInputKey((value) => value + 1); await load();
    } catch (value) { setError(errorMessage(value)); } finally { setPending(false); }
  }
  async function remove(item: GroupResource) {
    if (!window.confirm(t('이 자료를 삭제할까요?', 'Delete this resource?'))) return;
    try { await resourceApi.remove(item.id); setItems((current) => current.filter((value) => value.id !== item.id)); }
    catch (value) { setError(errorMessage(value)); }
  }
  return <section className="resource-panel task-action-section" aria-busy={loading}>
    <div className="task-section-heading"><div><span className="page-eyebrow">RESOURCES</span><h2>{taskId ? t('업무 첨부', 'Task attachments') : t('그룹 자료', 'Group resources')}</h2></div><strong>{items.length}</strong></div>
    {loading ? <p className="resource-loading">{t('자료를 불러오는 중...', 'Loading resources...')}</p> : items.length === 0 ? <p className="empty-state">{t('등록된 자료가 없습니다.', 'No resources yet.')}</p> :
      <div className="resource-list">{items.map((item) => <article key={item.id}><span className={`resource-type ${item.type.toLowerCase()}`}>{item.type}</span><div><strong>{item.title}</strong><small>{item.createdByNickname} · {new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', { dateStyle: 'medium' }).format(new Date(item.createdAt))}{item.sizeBytes ? ` · ${formatBytes(item.sizeBytes)}` : ''}</small></div><div className="resource-item-actions">{item.type === 'LINK' ? <a className="secondary" href={item.url} target="_blank" rel="noreferrer">{t('열기', 'Open')}</a> : <button className="secondary" type="button" onClick={() => resourceApi.download(item).catch((value) => setError(errorMessage(value)))}>{t('다운로드', 'Download')}</button>}{item.canDelete && <button className="danger" type="button" onClick={() => remove(item)}>{t('삭제', 'Delete')}</button>}</div></article>)}</div>}
    <form className="resource-form" onSubmit={submit}>
      <div className="resource-form-heading"><div><strong>{t('새 자료 추가', 'Add a new resource')}</strong><small>{t('업무에 필요한 링크나 파일을 한곳에 정리하세요.', 'Keep useful links and files together.')}</small></div>
        <div className="resource-tabs" role="group" aria-label={t('자료 유형', 'Resource type')}>
          <button aria-pressed={mode === 'LINK'} className={mode === 'LINK' ? 'active' : ''} type="button" onClick={() => setMode('LINK')}>{t('외부 링크', 'External link')}</button>
          <button aria-pressed={mode === 'FILE'} className={mode === 'FILE' ? 'active' : ''} type="button" onClick={() => setMode('FILE')}>{t('파일 첨부', 'File upload')}</button>
        </div>
      </div>
      <label className="resource-field">
        <span>{t('자료 제목', 'Resource title')}</span>
        <input required maxLength={120} value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t('예: 7월 프로젝트 기획서', 'e.g. July project brief')} />
      </label>
      {mode === 'LINK' ? <label className="resource-field">
        <span>{t('링크 주소', 'Link URL')}</span>
        <input required type="url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://github.com/ · Notion · Jira · Google Drive" />
      </label> :
        <div className="resource-field">
          <span>{t('첨부 파일', 'Attachment')}</span>
          <label className={`resource-file ${file ? 'selected' : ''}`} htmlFor={fileInputId}>
            <input key={fileInputKey} id={fileInputId} required type="file" accept=".pdf,.png,.jpg,.jpeg,.gif,.txt,.csv,.docx,.xlsx,.pptx,.zip" onChange={(event) => setFile(event.target.files?.[0])} />
            <i aria-hidden="true">{file ? '✓' : '＋'}</i>
            <span><strong>{file?.name ?? t('파일을 선택하세요', 'Choose a file')}</strong><small>{file ? formatBytes(file.size) : t('클릭하여 기기에서 파일 선택 · 최대 20MB', 'Choose from your device · up to 20MB')}</small></span>
          </label>
        </div>}
      <div className="resource-form-actions">
        <small>{mode === 'LINK' ? t('GitHub, Notion, Jira, Google Drive 등의 주소를 등록할 수 있습니다.', 'Supports GitHub, Notion, Jira, Google Drive, and other URLs.') : t('PDF, 이미지, 문서, 스프레드시트, 프레젠테이션, ZIP 파일을 지원합니다.', 'Supports PDFs, images, documents, spreadsheets, presentations, and ZIP files.')}</small>
        <button className="primary" type="submit" disabled={pending || !title.trim() || (mode === 'LINK' ? !url.trim() : !file)}>{pending ? t('등록 중...', 'Adding...') : mode === 'LINK' ? t('링크 등록', 'Add link') : t('파일 첨부', 'Upload file')}</button>
      </div>
    </form>{error && <p className="error resource-error">{error}</p>}
  </section>;
}
function formatBytes(value: number) { return value < 1024 * 1024 ? `${Math.ceil(value / 1024)}KB` : `${(value / 1024 / 1024).toFixed(1)}MB`; }
