import { useState } from 'react';
import { errorMessage } from '../../../api/client';
import { GroupResponse } from '../../../api/groupApi';
import { GenerateReportResponse, reportApi } from '../../../api/reportApi';
import { Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { zonedTodayString } from '../../../app/week';

type Props = {
  groupId: number;
  group?: GroupResponse;
  selection: {
    scope: 'MY' | 'GROUP';
    period: 'WEEKLY' | 'MONTHLY' | 'YEARLY';
    from: string;
    toExclusive: string;
    /** 대시보드가 쓰는 기간 이름. 예: `2026년 7월 4주차`. 이미 화면 언어로 만들어져 있다. */
    label: string;
  };
};

export function AiWeeklyReportAction({ groupId, group, selection }: Props) {
  const { t } = useLanguage();
  const [message, setMessage] = useState('');
  const [failed, setFailed] = useState(false);
  const [pending, setPending] = useState(false);
  const [showLanguageChoice, setShowLanguageChoice] = useState(false);
  /** 저장된 revision이 이미 있을 때만 채워진다. 재생성할지 물어보는 단계로 넘어간다. */
  const [existing, setExisting] = useState<{ language: 'KO' | 'EN'; report: GenerateReportResponse }>();

  const canManage = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const supportedSelection = selection.scope === 'GROUP';

  // 대시보드 기간을 그대로 사용한다. mondayOf 변환 없음.
  const fromDate = selection.from;
  const toExclusive = selection.toExclusive;

  // toExclusive가 오늘 이하여야 완료된 기간이다.
  const today = zonedTodayString(group?.timezone);
  const isCompletedPeriod = toExclusive <= today;

  async function handleGenerate(langCode: 'KO' | 'EN') {
    if (!isCompletedPeriod) {
      setFailed(true);
      setMessage(t(
        'AI 리포트는 완료된 기간만 생성할 수 있습니다.',
        'AI reports can only be generated for completed periods.'
      ));
      return;
    }
    setMessage('');
    setPending(true);
    try {
      // regenerate=false면 서버가 저장된 revision을 그대로 돌려준다. OpenAI는 부르지 않는다.
      const res = await reportApi.generateAiWeekly(groupId, {
        from: fromDate,
        toExclusive,
        language: langCode,
        regenerate: false,
      });

      if (!res.createdNew) {
        // 이미 만들어 둔 리포트가 있다. 유료 호출이라 사용자에게 먼저 물어본다.
        setExisting({ language: langCode, report: res });
        setFailed(false);
        setMessage('');
        return;
      }

      await download(res);
    } catch (caught) {
      setFailed(true);
      setMessage(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function download(report: GenerateReportResponse) {
    await reportApi.downloadAiWeeklyDocument(groupId, report.reportId, report.from, report.revision);
    setFailed(false);
    setExisting(undefined);
    // 결과 문구도 모달 안에서 보여 준다. 여기서 닫으면 읽을 틈이 없다.
    setMessage(t(
      'AI 리포트를 내려받았습니다. 파일을 열고 브라우저에서 PDF로 저장할 수 있습니다.',
      'The AI report was downloaded. Open it and save as PDF from your browser.',
    ));
  }

  /** 사용자가 재생성을 고른 경우에만 OpenAI를 다시 부른다. */
  async function handleExistingChoice(regenerate: boolean) {
    if (!existing) return;
    setMessage('');
    setPending(true);
    try {
      if (!regenerate) {
        await download(existing.report);
        return;
      }
      const created = await reportApi.generateAiWeekly(groupId, {
        from: fromDate,
        toExclusive,
        language: existing.language,
        regenerate: true,
      });
      await download(created);
    } catch (caught) {
      setFailed(true);
      setMessage(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  // 유료 팀의 팀장이 아니면 버튼 자체를 노출하지 않는다.
  if (!canManage) {
    return null;
  }

  const unavailableReason = !supportedSelection
    ? t(
      'AI 리포트는 그룹 전체 기본 리포트를 사용합니다.',
      'AI reports use whole-group reports.',
    )
    : !isCompletedPeriod
      ? t('AI 리포트는 완료된 기간만 생성할 수 있습니다.', 'AI reports can only be generated for completed periods.')
      : '';

  // 래퍼를 두지 않는다. 기본 리포트 버튼들과 같은 flex 줄에 직접 놓여야 높이와 간격이 맞는다.
  return (
    <>
      <button
        className="report-download ai-report-button"
        type="button"
        disabled={pending}
        title={unavailableReason || undefined}
        onClick={() => { setMessage(''); setExisting(undefined); setShowLanguageChoice(true); }}
      >
        {pending ? t('생성 중...', 'Generating...') : t('AI 리포트', 'AI report')}
      </button>

      {showLanguageChoice && existing && (
        <Modal
          title={t('이미 만든 리포트가 있습니다', 'A report already exists')}
          description={t(
            `${selection.label} ${existing.language === 'KO' ? '한국어' : '영문'} 리포트 R${existing.report.revision}이 이미 있습니다. 새로 생성할까요?`,
            `${existing.language === 'KO' ? 'Korean' : 'English'} report R${existing.report.revision} for ${selection.label} already exists. Generate a new one?`,
          )}
          hideCloseIcon
          onClose={() => { if (!pending) { setExisting(undefined); setShowLanguageChoice(false); } }}
        >
          <p style={{ color: '#8a8490', fontSize: '12px', margin: '0 0 4px' }}>
            {t(
              '새로 생성하면 AI 분석을 다시 실행하고 새 리비전으로 저장합니다.',
              'Generating a new one runs the AI analysis again and stores a new revision.',
            )}
          </p>
          {message && <p className={failed ? 'error' : 'success-message'}>{message}</p>}

          <div className="modal-actions">
            <button
              className="report-download"
              type="button"
              autoFocus
              disabled={pending}
              onClick={() => void handleExistingChoice(true)}
            >
              {pending ? t('생성 중...', 'Generating...') : t('예, 새로 생성', 'Yes, generate new')}
            </button>
            <button
              className="secondary"
              type="button"
              disabled={pending}
              onClick={() => void handleExistingChoice(false)}
            >
              {t('아니요, 기존 리포트 받기', 'No, download the existing one')}
            </button>
            <button
              className="secondary"
              type="button"
              disabled={pending}
              onClick={() => { setExisting(undefined); setShowLanguageChoice(false); }}
            >
              {t('닫기', 'Close')}
            </button>
          </div>
        </Modal>
      )}

      {showLanguageChoice && !existing && (
        <Modal
          title={t('AI 리포트 언어 선택', 'Choose AI report language')}
          description={t(
            `${selection.label} 리포트를 내려받을 언어를 고르세요.`,
            `Pick the language for the ${selection.label} report.`,
          )}
          hideCloseIcon
          onClose={() => { if (!pending) setShowLanguageChoice(false); }}
        >
          {/* 생성할 수 없는 이유와 결과 문구는 모두 이 안에서만 보여 준다. */}
          {unavailableReason && <p className="error">{unavailableReason}</p>}
          {message && <p className={failed ? 'error' : 'success-message'}>{message}</p>}

          <div className="modal-actions">
            <button
              className="report-download"
              type="button"
              autoFocus
              disabled={pending || Boolean(unavailableReason)}
              onClick={() => void handleGenerate('KO')}
            >
              {pending ? t('생성 중...', 'Generating...') : '한국어 다운로드'}
            </button>
            <button
              className="secondary"
              type="button"
              disabled={pending || Boolean(unavailableReason)}
              onClick={() => void handleGenerate('EN')}
            >
              {pending ? t('생성 중...', 'Generating...') : 'English download'}
            </button>
            <button
              className="secondary"
              type="button"
              disabled={pending}
              onClick={() => setShowLanguageChoice(false)}
            >
              {t('닫기', 'Close')}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
