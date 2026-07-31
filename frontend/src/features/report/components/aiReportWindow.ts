import { errorMessage } from '../../../api/client';
import type { ApiError } from '../../../api/client';
import { AiWeeklyReportView, reportApi } from '../../../api/reportApi';

type Translate = (ko: string, en: string) => string;

export function openReportWindow() {
  return window.open('', '_blank', 'width=980,height=820');
}

export async function resolveWeeklyAiReport(
  groupId: number,
  from: string,
  language: 'KO' | 'EN',
): Promise<AiWeeklyReportView> {
  const d = new Date(from);
  d.setDate(d.getDate() + 7);
  const toExclusive = d.toISOString().split('T')[0];

  const genRes = await reportApi.generateAiWeekly(groupId, {
    from,
    toExclusive,
    language,
    regenerate: false,
  });

  return await reportApi.findAiWeeklyById(groupId, genRes.reportId);
}

export function writeGeneratingWindow(target: Window, t: Translate, language: 'KO' | 'EN') {
  target.document.open();
  target.document.write(`<!doctype html><html lang="${language.toLowerCase()}"><head><meta charset="utf-8">`
    + `<meta name="viewport" content="width=device-width,initial-scale=1">`
    + `<title>${escapeHtml(t('AI 주간 리포트 생성 중', 'Generating AI weekly report'))}</title>`
    + `<style>body{font-family:sans-serif;display:grid;place-items:center;min-height:100vh;margin:0;background:#f8fafc;color:#1e293b}</style>`
    + `</head><body><div style="text-align:center">`
    + `<h2>${escapeHtml(t('v7-2 AI 주간 리포트를 생성하고 있습니다.', 'Building the v7-2 AI weekly report.'))}</h2>`
    + `<p>${escapeHtml(t('잠시만 기다려 주세요...', 'Please wait a moment...'))}</p>`
    + `</div></body></html>`);
  target.document.close();
}

export function writeReportWindow(target: Window, report: AiWeeklyReportView, options: {
  t: Translate;
  language: 'KO' | 'EN';
  onDownload: () => Promise<void>;
}) {
  const { t, language, onDownload } = options;

  target.document.open();
  target.document.write(`<!doctype html><html lang="${language.toLowerCase()}"><head><meta charset="utf-8">`
    + `<meta name="viewport" content="width=device-width,initial-scale=1">`
    + `<title>${escapeHtml(t('AI 주간 리포트 v7-2', 'AI Weekly Report v7-2'))}</title>`
    + `<style>body{font-family:sans-serif;padding:24px;background:#fff;color:#1e293b} .card{background:#f8fafc;padding:16px;margin:12px 0;border-radius:8px}</style>`
    + `</head><body>`
    + `<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:24px">`
    + `<h1>v7-2 AI WEEKLY REPORT</h1>`
    + `<button id="ai-report-pdf" style="background:#059669;color:#fff;padding:10px 18px;border:none;border-radius:6px;cursor:pointer;font-weight:600">${escapeHtml(t('PDF 다운로드', 'Download PDF'))}</button>`
    + `</div>`
    + `<p>기간: ${report.from} ~ ${report.toExclusive} · 리비전: R${report.revision}</p>`
    + `<div class="card">`
    + `<h2>${escapeHtml(report.executiveJudgment?.headline || '주간 요약')}</h2>`
    + `<p>${escapeHtml(report.executiveJudgment?.interpretation || '')}</p>`
    + `</div>`
    + `</body></html>`);
  target.document.close();

  const pdfButton = target.document.getElementById('ai-report-pdf') as HTMLButtonElement | null;
  pdfButton?.addEventListener('click', () => {
    pdfButton.disabled = true;
    void onDownload().finally(() => { pdfButton.disabled = false; });
  });
  target.focus();
}

export function aiReportMessage(error: ApiError, t: Translate) {
  return errorMessage(error);
}

function escapeHtml(value: string) {
  return value.replace(/[&<>'"]/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  })[character] ?? character);
}
