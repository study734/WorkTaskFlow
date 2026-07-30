import { errorMessage } from '../../../api/client';
import type { ApiError } from '../../../api/client';
import { reportApi, WeeklyAiReport } from '../../../api/reportApi';

// 생성 중이면 서버가 AI_REPORT_GENERATING을 돌려주므로 lease(4m) 안에서만 재확인한다.
const POLL_INTERVAL_MS = 3000;
const POLL_LIMIT = 80;

type Translate = (ko: string, en: string) => string;

// 팝업 차단을 통과하려면 클릭 핸들러에서 동기로 열어야 한다. 생성은 연 뒤에 시작한다.
export function openReportWindow() {
  return window.open('', '_blank', 'width=980,height=820');
}

export async function resolveWeeklyAiReport(
  groupId: number,
  weekStart: string,
  language: 'ko' | 'en',
): Promise<WeeklyAiReport> {
  async function waitForCompletion(attempt: number): Promise<WeeklyAiReport> {
    if (attempt >= POLL_LIMIT) {
      throw {
        code: 'AI_REPORT_GENERATING',
        message: '리포트 생성이 예상보다 오래 걸립니다. 잠시 후 다시 시도해 주세요.',
      } as ApiError;
    }
    await new Promise((resolve) => window.setTimeout(resolve, POLL_INTERVAL_MS));
    try {
      const report = await reportApi.findWeeklyAi(groupId, weekStart, language);
      if (report.status === 'GENERATING' || report.status === 'PENDING') {
        return waitForCompletion(attempt + 1);
      }
      return report;
    } catch (caught) {
      if ((caught as ApiError).code === 'AI_REPORT_GENERATING') {
        return waitForCompletion(attempt + 1);
      }
      throw caught;
    }
  }

  try {
    const existing = await reportApi.findWeeklyAi(groupId, weekStart, language);
    if (existing.status === 'COMPLETED') return existing;
    if (existing.status === 'GENERATING' || existing.status === 'PENDING') {
      return waitForCompletion(0);
    }
    // 실패한 revision만 남아 있으면 다시 생성해야 읽을 내용이 생긴다.
  } catch (caught) {
    const code = (caught as ApiError).code;
    if (code === 'AI_REPORT_GENERATING') return waitForCompletion(0);
    if (code && code !== 'AI_REPORT_NOT_FOUND') throw caught;
  }
  try {
    return await reportApi.generateWeeklyAi(groupId, weekStart, language);
  } catch (caught) {
    if ((caught as ApiError).code === 'AI_REPORT_GENERATING') return waitForCompletion(0);
    throw caught;
  }
}

export function writeGeneratingWindow(target: Window, t: Translate, language: 'ko' | 'en') {
  target.document.open();
  target.document.write(`<!doctype html><html lang="${language}"><head><meta charset="utf-8">`
    + `<meta name="viewport" content="width=device-width,initial-scale=1">`
    + `<title>${escapeHtml(t('AI 주간 리포트 생성 중', 'Generating AI weekly report'))}</title>`
    + `<style>${BASE_STYLE}
    .waiting{display:grid;place-items:center;gap:14px;min-height:100vh;padding:32px;text-align:center}
    .spinner{width:34px;height:34px;border:3px solid #e3ddf5;border-top-color:#6657bd;border-radius:50%;animation:spin 900ms linear infinite}
    @keyframes spin{to{transform:rotate(360deg)}}
    .waiting strong{color:#2f2a3a;font-size:18px;letter-spacing:-.02em}
    .waiting p{max-width:420px;margin:0;color:#7c7688;font-size:13px;line-height:1.6}
    </style></head><body><div class="waiting" aria-live="polite" aria-busy="true">`
    + `<div class="spinner" aria-hidden="true"></div>`
    + `<strong>${escapeHtml(t('AI 주간 리포트를 만들고 있습니다.', 'Building the AI weekly report.'))}</strong>`
    + `<p>${escapeHtml(t(
      '완료되면 이 창에 바로 표시됩니다. 최대 1~2분 걸릴 수 있으니 창을 닫지 말아 주세요.',
      'It appears in this window when ready. This can take a minute or two, so please keep it open.',
    ))}</p></div></body></html>`);
  target.document.close();
}

export function writeReportWindow(target: Window, report: WeeklyAiReport, options: {
  t: Translate;
  language: 'ko' | 'en';
  onDownload: () => Promise<void>;
}) {
  const { t, language, onDownload } = options;
  const analysis = report.analysis;
  const metrics = report.metrics;
  const groupName = report.operations.groupName;
  const period = `${report.periodStart} ~ ${report.periodEnd}`;
  const generatedAt = report.generatedAt
    ? new Date(report.generatedAt).toLocaleString(language === 'ko' ? 'ko-KR' : 'en-US')
    : '-';

  const metricCards = [
    [t('전체 업무', 'Total tasks'), String(metrics.totalTasks), false],
    [t('완료율', 'Completion rate'), rate(metrics.completionRatePercent), false],
    [t('기한 준수율', 'On-time rate'), rate(metrics.onTimeRatePercent), false],
    [t('지연 업무', 'Overdue tasks'), String(metrics.statuses.delayed), metrics.statuses.delayed > 0],
    [t('보류 업무', 'On hold'), String(metrics.statuses.onHold), metrics.statuses.onHold > 0],
    [t('완료 업무', 'Completed'), String(metrics.statuses.completed), false],
    [t('평균 완료 시간', 'Avg. completion'), metrics.averageCompletionHours == null
      ? '-' : t(`${metrics.averageCompletionHours}시간`, `${metrics.averageCompletionHours}h`), false],
    [t('체크리스트', 'Checklist'), `${metrics.checklist.completed}/${metrics.checklist.total}`, false],
  ] as [string, string, boolean][];

  const body = [
    analysis && section(t('요약', 'Summary'), t('AI 해석', 'AI interpretation'),
      `<div class="prose"><p>${escapeHtml(analysis.summary.text)}</p></div>`),
    analysis && analysis.risks.length > 0 && section(t('위험', 'Risks'),
      t(`${analysis.risks.length}건`, `${analysis.risks.length}`),
      `<ul class="stack">${analysis.risks.map((risk) =>
        `<li><span class="severity ${risk.severity.toLowerCase()}">${severityLabel(risk.severity, t)}</span>`
        + `<p>${escapeHtml(risk.text)}</p></li>`).join('')}</ul>`),
    analysis && analysis.topActions.length > 0 && section(t('다음 주 우선 행동', 'Priority actions'),
      t('우선순위 순', 'By priority'),
      `<ol class="actions">${analysis.topActions.map((action) =>
        `<li><span class="priority">P${action.priority}</span><div>`
        + `<strong>${escapeHtml(action.action)}</strong>`
        + `<p>${escapeHtml(action.reason)}</p></div></li>`).join('')}</ol>`),
    analysis && analysis.leaderDecisions.length > 0 && section(t('팀장 결정', 'Leader decisions'),
      t('합의 필요', 'Needs a decision'),
      `<ul class="stack">${analysis.leaderDecisions.map((decision) =>
        `<li><strong>${escapeHtml(decision.question)}</strong>`
        + `<p>${escapeHtml(decision.impact)}</p></li>`).join('')}</ul>`),
  ].filter(Boolean).join('');

  target.document.open();
  target.document.write(`<!doctype html><html lang="${language}"><head><meta charset="utf-8">`
    + `<meta name="viewport" content="width=device-width,initial-scale=1">`
    + `<title>${escapeHtml(groupName)} ${escapeHtml(period)} ${escapeHtml(t('AI 리포트', 'AI report'))}</title>`
    + `<style>${BASE_STYLE}${REPORT_STYLE}</style></head><body>`
    + `<div class="toolbar no-print">`
    + `<span class="toolbar-status" id="ai-report-status" role="status"></span>`
    + `<button class="ghost-button" type="button" id="ai-report-print">`
    + `${escapeHtml(t('인쇄', 'Print'))}</button>`
    + `<button class="pdf-button" type="button" id="ai-report-pdf">`
    + `${escapeHtml(t('PDF 다운로드', 'Download PDF'))}</button></div>`
    + `<article class="report"><header>`
    + `<div class="brand"><span class="brand-mark">✓</span> TOESA · 퇴사</div>`
    + `<p class="eyebrow">AI WEEKLY REPORT</p>`
    + `<h1 class="hero-title">${escapeHtml(analysis?.headline
      ?? t('AI 주간 리포트', 'AI weekly report'))}</h1>`
    + `<div class="meta"><span>${escapeHtml(groupName)}</span><span>${escapeHtml(period)}</span>`
    + `<span>${escapeHtml(t('생성', 'Generated'))} ${escapeHtml(generatedAt)}</span>`
    + `<span class="publication ${report.publicationStatus === 'FINALIZED' ? 'final' : 'draft'}">`
    + `${escapeHtml(publicationLabel(report.publicationStatus, t))}</span></div>`
    + `</header><main>`
    + `<section class="metrics">${metricCards.map(([caption, value, risk]) =>
      `<div class="metric${risk ? ' risk' : ''}"><small>${escapeHtml(caption)}</small>`
      + `<strong>${escapeHtml(value)}</strong></div>`).join('')}</section>`
    + body
    + `<footer><span>${escapeHtml(t(
      '수치는 서버가 동결한 확정 지표이며, 서술은 AI 해석입니다.',
      'Figures are server-frozen metrics; the narrative is an AI interpretation.',
    ))}</span><strong>TOESA · WORK SMARTER, LEAVE ON TIME</strong></footer>`
    + `</main></article></body></html>`);
  target.document.close();

  const status = target.document.getElementById('ai-report-status');
  const pdfButton = target.document.getElementById('ai-report-pdf') as HTMLButtonElement | null;
  target.document.getElementById('ai-report-print')
    ?.addEventListener('click', () => target.print());
  pdfButton?.addEventListener('click', () => {
    pdfButton.disabled = true;
    if (status) status.textContent = t('PDF를 만드는 중...', 'Preparing the PDF...');
    void onDownload()
      .then(() => {
        if (status) status.textContent = t('PDF를 내려받았습니다.', 'The PDF has been downloaded.');
      })
      .catch((caught) => {
        if (status) status.textContent = errorMessage(caught);
      })
      .finally(() => { pdfButton.disabled = false; });
  });
  target.focus();
}

export function aiReportMessage(error: ApiError, t: Translate) {
  const known: Record<string, [string, string]> = {
    AI_REPORT_PAID_REQUIRED: [
      'AI 주간 리포트는 유료 그룹에서만 사용할 수 있습니다.',
      'AI weekly reports are available to paid groups only.',
    ],
    GROUP_LEADER_REQUIRED: [
      'AI 주간 리포트는 그룹 팀장만 생성할 수 있습니다.',
      'Only the group leader can generate AI weekly reports.',
    ],
    AI_REPORT_WEEK_INCOMPLETE: [
      '아직 끝나지 않은 주는 리포트를 만들 수 없습니다. 지난주를 선택해 주세요.',
      'A week that has not finished yet cannot be reported. Choose a completed week.',
    ],
    AI_REPORT_NOT_CONFIGURED: [
      'AI 리포트가 아직 설정되지 않았습니다. 관리자에게 문의해 주세요.',
      'AI reporting is not configured yet. Please contact an administrator.',
    ],
    AI_REPORT_INSUFFICIENT_DATA: [
      '해당 주에 분석할 업무 활동이 충분하지 않습니다.',
      'There is not enough task activity in that week to analyse.',
    ],
    AI_REPORT_WEEKLY_LIMIT: [
      '이번 주 생성 한도(3회)를 모두 사용했습니다.',
      'You have used all three generations for this week.',
    ],
    AI_REPORT_GENERATING: [
      '리포트 생성이 예상보다 오래 걸립니다. 잠시 후 다시 시도해 주세요.',
      'Generation is taking longer than expected. Please try again shortly.',
    ],
  };
  const entry = error.code ? known[error.code] : undefined;
  return entry ? t(entry[0], entry[1]) : errorMessage(error);
}

function section(title: string, note: string, content: string) {
  return `<section class="section"><div class="section-heading"><h2>${escapeHtml(title)}</h2>`
    + `<p>${escapeHtml(note)}</p></div>${content}</section>`;
}

function severityLabel(severity: 'LOW' | 'MEDIUM' | 'HIGH', t: Translate) {
  if (severity === 'HIGH') return t('높음', 'High');
  return severity === 'MEDIUM' ? t('보통', 'Medium') : t('낮음', 'Low');
}

function publicationLabel(status: WeeklyAiReport['publicationStatus'], t: Translate) {
  if (status === 'FINALIZED') return t('확정', 'Finalized');
  if (status === 'SUPERSEDED') return t('대체됨', 'Superseded');
  return t('초안', 'Draft');
}

function rate(value?: number) {
  return value == null ? '-' : `${value}%`;
}

function escapeHtml(value: string) {
  return value.replace(/[&<>'"]/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  })[character] ?? character);
}

const BASE_STYLE = `@page{size:A4;margin:12mm}*{box-sizing:border-box}
body{margin:0;background:#f3f1ec;color:#292731;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans KR",Arial,sans-serif;line-height:1.55}`;

const REPORT_STYLE = `
.toolbar{display:flex;align-items:center;justify-content:flex-end;gap:10px;width:min(920px,calc(100% - 32px));margin:18px auto 0}
.toolbar-status{margin-right:auto;color:#6f6980;font-size:12px}
.pdf-button{padding:10px 15px;border:0;border-radius:11px;color:#fff;background:#6557b4;font:inherit;font-size:12px;font-weight:750;cursor:pointer;box-shadow:0 7px 18px rgba(85,69,160,.2)}
.pdf-button:disabled{opacity:.6;cursor:progress}
.ghost-button{padding:10px 15px;border:1px solid #ded8ea;border-radius:11px;color:#5d5670;background:#fff;font:inherit;font-size:12px;font-weight:700;cursor:pointer}
.report{width:min(920px,calc(100% - 32px));margin:12px auto 28px;background:#fff;border:1px solid #e7e3dc;border-radius:24px;box-shadow:0 18px 55px rgba(52,45,70,.09);overflow:hidden}
header{position:relative;padding:30px 34px 32px;background:linear-gradient(135deg,#fff 0%,#f5f1ff 58%,#eee9ff 100%);border-bottom:1px solid #e6e0f3;overflow:hidden}
header:after{content:"";position:absolute;right:-54px;top:-84px;width:220px;height:220px;border:42px solid rgba(103,84,176,.08);border-radius:50%}
.brand{display:flex;align-items:center;gap:10px;margin-bottom:30px;color:#5e55b7;font-size:12px;font-weight:800;letter-spacing:.13em}
.brand-mark{display:inline-grid;place-items:center;width:32px;height:32px;border-radius:10px;color:#fff;background:#6657bd;font-size:15px;letter-spacing:0}
.eyebrow{margin:0 0 8px;color:#746a90;font-size:11px;font-weight:800;letter-spacing:.14em}
.hero-title{position:relative;max-width:650px;margin:0;color:#292333;font-size:29px;line-height:1.3;letter-spacing:-.035em}
.meta{display:flex;flex-wrap:wrap;align-items:center;gap:7px 15px;margin-top:14px;color:#726d79;font-size:13px}
.meta span{display:inline-flex;align-items:center;gap:6px}.meta span:before{content:"";width:5px;height:5px;border-radius:50%;background:#8b7bd2}
.publication{padding:3px 9px;border-radius:99px;font-size:11px;font-weight:750}
.publication:before{display:none}
.publication.final{color:#32735b;background:#e5f4ec}
.publication.draft{color:#8a681c;background:#fff2ce}
.draft-note{position:relative;margin:14px 0 0;color:#7a6a4a;font-size:12px}
main{padding:30px 34px 34px}
.metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}
.metric{min-height:96px;padding:17px;border:1px solid #e8e4ee;border-radius:16px;background:#fcfbfd}
.metric small{display:block;margin-bottom:10px;color:#7d7785;font-size:11px;font-weight:700}
.metric strong{display:block;color:#302b39;font-size:24px;line-height:1}
.metric.risk{background:#fff9f7;border-color:#f1ded7}.metric.risk strong{color:#c15b44}
.section{margin-top:29px}
.section-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:12px}
.section-heading h2{margin:0;color:#332e3c;font-size:18px;letter-spacing:-.02em}
.section-heading p{margin:0;color:#8a8490;font-size:11px}
.prose{padding:18px 20px;border:1px solid #e6e0f2;border-radius:16px;background:#faf8ff}
.prose p{margin:0;color:#544d63;font-size:14px;line-height:1.7}
.stack{display:grid;gap:10px;margin:0;padding:0;list-style:none}
.stack li{padding:15px 17px;border:1px solid #eae6ef;border-radius:14px;background:#fcfbfd}
.stack strong{display:block;color:#35303c;font-size:13px}
.stack p{margin:6px 0 0;color:#635d6e;font-size:13px}
.severity{display:inline-flex;padding:3px 9px;border-radius:99px;color:#5f5869;background:#eeeaf2;font-size:10px;font-weight:750}
.severity.high{color:#a2483d;background:#fbe9e5}.severity.medium{color:#8a681c;background:#fff2ce}
.actions{display:grid;gap:10px;margin:0;padding:0;list-style:none}
.actions li{display:flex;gap:13px;padding:15px 17px;border:1px solid #eae6ef;border-radius:14px;background:#fcfbfd}
.priority{flex:none;display:grid;place-items:center;width:30px;height:24px;border-radius:8px;color:#6658ad;background:#ece7fb;font-size:11px;font-weight:800}
.actions strong{display:block;color:#35303c;font-size:13px}
.actions p{margin:6px 0 0;color:#635d6e;font-size:13px}
footer{display:flex;justify-content:space-between;gap:18px;margin-top:26px;padding-top:18px;border-top:1px solid #ece8ee;color:#8a8490;font-size:10px}
footer strong{color:#655c73;white-space:nowrap}
@media print{body{background:#fff}.no-print{display:none}.report{width:100%;margin:0;border:0;border-radius:0;box-shadow:none}header{padding:24px 26px}main{padding:24px 26px}.section,tr{break-inside:avoid}}
@media(max-width:700px){.report{width:100%;margin:0;border:0;border-radius:0}.toolbar{width:calc(100% - 32px)}.metrics{grid-template-columns:repeat(2,1fr)}header,main{padding:24px 20px}.hero-title{font-size:24px}footer{flex-direction:column}}
`;
