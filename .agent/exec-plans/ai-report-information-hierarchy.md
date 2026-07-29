# Reframe the AI report around leader decisions and meeting use

This ExecPlan is a living document. The sections `Progress`,
`Surprises & Discoveries`, `Decision Log`, and `Outcomes & Retrospective` must
be kept up to date as work proceeds.

This plan follows `.agent/PLANS.md`.

## Purpose / Big Picture

The basic report card should offer an `AI 리포트` action beside PDF generation
without a separate AI tab or summary dashboard. The AI report should read as an extension of the basic report:
server-derived task counts, progress, dates, and evidence remain the factual
basis, while AI adds interpretation, risks, decisions, and next actions.

The detail route keeps three display densities with explicit jobs:

- `표준` is the default leader view for seeing project process, outcomes,
  progress, risks, and next actions at a glance.
- `요약` is a meeting brief containing the key judgement, discussion risks,
  decisions that need agreement, and follow-up actions.
- `상세` adds daily, member, task, and evidence-level material for verification
  and follow-up.

## Task Brief

**Goal:** Reframe the dashboard entry and AI detail information hierarchy so the
AI report extends the basic report data and each density has a clear use.

**Context:** The affected surfaces are
`frontend/src/features/report/components/AiWeeklyReportPanel.tsx`,
`frontend/src/features/report/pages/AiWeeklyReportDetailPage.tsx`,
`frontend/src/features/report/components/AiReportContent.tsx`,
`frontend/e2e/ai-weekly-report.chrome.spec.ts`, and
`docs/spec/AiWeeklyReport.md`.

**Constraints:** Preserve the dirty worktree and all unrelated WIP. Work only on
`member1-work`; do not push. Keep deterministic metrics and dates as server
facts and retain AI interpretation labels and evidence drill-down. Do not remove
the broader group dashboard in this task because it owns task, schedule, member,
and risk navigation outside the report contract. Do not change provider,
persistence, authorization, or PDF contracts.

**Done criteria:**

- [x] The basic report card contains an `AI 리포트` action and no separate AI
      tab or compact AI summary dashboard.
- [x] The detail density selector explains the user job of all three modes and
      defaults to `표준`.
- [x] Summary includes meeting decisions and follow-up actions without detailed
      daily, member, or task evidence tables.
- [x] Standard retains leader-level process, outcome, progress, risk, and action
      information.
- [x] Detailed retains daily, member, task, and evidence-level information.
- [x] The focused E2E and `npm run build` pass, or any environment block is
      recorded precisely.
- [x] A separate diff review finds no scope drift, secrets, or unrelated files.

## Progress

- [x] (2026-07-29 00:00Z) Inspected the compact entry, detail density controls,
  current density projection, tests, and product contract.
- [x] (2026-07-29 00:00Z) Chose a frontend information-hierarchy change that
  preserves the current backend and PDF contracts.
- [x] (2026-07-29 00:00Z) Replaced the separate AI tab and compact dashboard
  with an AI action that shares the basic report scope and period.
- [x] (2026-07-29 00:00Z) Added detail density guidance and meeting decisions
  and actions to summary mode.
- [x] (2026-07-29 00:00Z) Updated the product contract and focused E2E
  assertions.
- [x] (2026-07-29 00:00Z) Kept the report control row visually stable for
  unsupported selections and made GROUP the leader-first scope option.
- [x] (2026-07-29 00:00Z) Reframed the detail route as a document reader by
  removing duplicate dashboard headings and demoting view controls to toolbars.
- [x] (2026-07-29 00:00Z) Raised paid-report quality with an evidence-bound v5
  editorial prompt and moved decisions and actions ahead of detailed metrics.
- [x] (2026-07-29 00:00Z) Production build, focused backend tests, and the full
  13-case AI report Chrome E2E suite passed.
- [x] (2026-07-29 00:00Z) Completed the separate diff and stale-selector review.

## Surprises & Discoveries

- Observation: The existing default density is already `STANDARD`; no URL or
  persistence migration is needed.
  Evidence: `DEFAULT_REPORT_PROJECTION_STATE` in
  `frontend/src/features/report/reportProjection.ts`.

- Observation: Current summary mode hides leader decisions and only keeps one
  action, which is too thin for meeting preparation.
  Evidence: `visibleActions` slices to one and the `AI 분석` section containing
  decisions is hidden when density is `SUMMARY`.

- Observation: The AI report already uses a frozen server snapshot containing
  metrics, team operations, tasks, and evidence. Replacing that data path with a
  new basic-report API would duplicate facts and weaken reproducibility.
  Evidence: `CompletedWeeklyAiReport` is rendered by `AiReportContent`, while
  numeric fields come from `report.metrics` and `report.operations`.

## Decision Log

- Decision: Keep the broader group dashboard but remove its separate AI tab and
  compact AI summary dashboard. Place the AI action in the basic report card.
  Rationale: The group dashboard still owns task, calendar, workload, and risk
  navigation, while the report surface now has one factual basis and two
  actions: PDF and AI interpretation.
  Date/Author: 2026-07-29 / Codex.

- Decision: Reuse the current frozen report response as the shared factual basis.
  Rationale: It already contains the deterministic data used by the basic report
  concepts and preserves revision reproducibility and evidence links.
  Date/Author: 2026-07-29 / Codex.

- Decision: Make summary a meeting brief by exposing up to three decisions and
  actions, while keeping detailed tables out.
  Rationale: Meetings require explicit decisions and owners, not only a short
  narrative.
  Date/Author: 2026-07-29 / Codex.

- Decision: Preserve the current GROUP-WEEKLY generation contract while making
  unsupported selections match the GROUP control-row layout.
  Rationale: The backend cannot generate personal, monthly, or yearly AI
  revisions. Keeping the reason in the disabled button's tooltip and accessible
  description avoids a misleading API expansion and removes the layout jump.
  Date/Author: 2026-07-29 / Codex.

- Decision: Use the frozen report headline as the single visible page heading
  and treat density, revision, and scope as reader controls.
  Rationale: The user enters this route to read and act on a report, not to
  operate a second dashboard. The document should therefore precede its view
  configuration in the visual hierarchy.
  Date/Author: 2026-07-29 / Codex.

- Decision: Improve narrative quality without expanding the frozen schema or
  provider privacy boundary.
  Rationale: Existing fields already support a concrete diagnosis, up to three
  actions, leader decisions, risks, changes, and evidence. Stronger editorial
  instructions and information order provide the missing value without a
  migration or new personal data.
  Date/Author: 2026-07-29 / Codex.

## Outcomes & Retrospective

The dashboard now has one report control surface: PDF and AI actions share the
basic report selectors, with no separate AI tab or compact summary dashboard.
GROUP is the leader-first scope. Switching to an unsupported MY, MONTHLY, or
YEARLY selection disables AI without adding visible helper copy or changing the
control-row geometry; the reason remains available through the button tooltip
and accessible description.

The opened AI route now reads as a report document. Duplicate page and panel
titles are gone, the analysis headline is the sole visible `h1`, view density
shows as a compact reader option, and revision/scope/state share one thin
toolbar above the frozen document.

The paid narrative contract now uses prompt v5. It bans unqualified generic
advice, prioritizes the dominant movement, and requires operational consequence,
decision, and concrete follow-up. The web reader adds a deterministic
server-fact signal for old revisions and shows actions and decisions before the
long KPI and risk detail.

The detail route defaults to the leader-oriented standard view, describes the
jobs of standard, summary, and detailed modes, and gives summary mode meeting
decisions plus bounded follow-up actions.

Validation:

- `npm.cmd run build` passed (`tsc -b && vite build`, 81 modules).
- `rtk git diff --check` passed.
- Full Playwright AI-report regression passed: 13/13 Chrome scenarios.
- Running-app inspection confirmed GROUP as the default and first scope option,
  the AI button enabled for GROUP-WEEKLY, disabled for MY, and identical
  64-pixel row / 42-pixel button geometry in both scope states.
- Running-app inspection of the detail route confirmed one document `h1`,
  revision R1, GROUP scope, STANDARD view, and the finalized PDF action.
- Focused backend tests passed: 30 tests across `NarrativeContractTest`,
  `OpenAiResponsesNarrativeAdapterTest`, and `WeeklyReportModuleTest`.
- The stale UI search found no production references to `surface`,
  `reportMode`, `report-mode-tabs`, `ai-report-compact-summary`, or
  `전체 리포트 열기`.

## Context and Orientation

`GroupDashboardPage` renders the basic report card.
`AiWeeklyReportAction` looks up or generates the matching AI revision from the
same scope and period selection, then opens the detail route.

`AiWeeklyReportDetailPage` owns the report header, URL-backed scope and density
controls, and renders `AiWeeklyReportPanel` with `surface="detail"`.

`AiReportContent` renders the frozen report response. Density is a client-side
projection only: it must not call the provider, regenerate a report, change
revision state, or modify stored JSON.

The checkout is intentionally dirty with the larger AI-report WIP. All edits
must preserve unrelated changes. Personal plan files remain on
`member1-work` and are excluded from a curated final submission.

## Plan of Work

Remove the report-mode tabs and compact AI summary from `GroupDashboardPage`.
Add `AiWeeklyReportAction` beside PDF generation so both actions use the same
basic report selection. Add concise purpose text beside the density controls in
`AiWeeklyReportDetailPage` so a leader can choose the correct mode without
guessing.

In `AiReportContent`, keep standard and detailed projections intact. For summary
mode, show a meeting-specific section with a bounded list of leader decisions
and keep up to three next actions. Continue hiding daily flow, member tables,
full task cards, and the general analysis grid in summary.

Update the E2E assertions for the new action label, mode guidance, and meeting
brief boundaries. Update `docs/spec/AiWeeklyReport.md` so the density contract
states the user job rather than only enumerating visible rows.

## Milestones

Milestone 1 changes the dashboard entry and density guidance. It is accepted
when no AI tab or compact AI dashboard remains, the basic report card has an
`AI 리포트` action, the route remains unchanged, and the detail page visibly
explains `표준`, `요약`, and `상세`.

Milestone 2 changes summary into a meeting brief. It is accepted when summary
shows decision items and up to three actions while detailed operational tables
remain absent; standard and detailed continue to expose their current content.

Milestone 3 updates contracts and validates. It is accepted when the focused
Playwright scenario and production build pass, `git diff --check` reports no
errors, and a separate review finds no scope drift.

## Concrete Steps

From `frontend/`, run:

    npm.cmd run test:e2e -- --grep "AI 리포트|표시 밀도"
    npm.cmd run build

From the repository root, run:

    rtk git diff --check
    rtk git diff -- frontend/src/features/report frontend/e2e/ai-weekly-report.chrome.spec.ts docs/spec/AiWeeklyReport.md frontend/src/styles.css

The focused E2E should report zero failures. The build should complete TypeScript
and Vite production output. Diff check should print no errors.

## Validation and Acceptance

On `/groups/1/dashboard`, the basic report card should show PDF and `AI 리포트`
actions under the same scope and period. No AI tab or compact AI summary should
remain. Activating the AI action should navigate to
`/groups/1/reports/ai-weekly/{reportId}` with standard selected by default.

The detail header should explain:

- standard is for leader overview;
- summary is for meeting preparation;
- detailed is for evidence and follow-up.

Summary must show a meeting decision section and bounded actions, but no daily
flow, team work flow, or full task cards. Standard must show seven team KPIs,
risks, achievements, next actions, and AI analysis. Detailed must add daily,
member, and task detail.

## Idempotence and Recovery

All edits are frontend projections, copy, tests, and documentation; they are
safe to repeat. No database or stored report migration is involved. If a test
fails, retain the existing backend response and repair only the projection or
assertion. Never reset or discard the dirty worktree.

## Artifacts and Notes

The current browser comment identifies the compact `전체 리포트 열기` link as
the entry to rename and reframe.

## Interfaces and Dependencies

No new library or API is required. The work depends on:

- `CompletedWeeklyAiReport` from `frontend/src/api/reportApi.ts`;
- `ReportDensity` and `AiReportContent`;
- URL-backed `ReportProjectionState`;
- the existing detail route
  `/groups/:groupId/reports/ai-weekly/:reportId`.

## Revision note

2026-07-29: Initial plan created after inspecting the current compact card,
density projection, E2E coverage, and product contract.

2026-07-29: Revised the plan after clarifying that the separate AI tab and
compact dashboard should be removed. The AI action now lives in the basic report
card and reuses its selection.

2026-07-29: Revised the dashboard control contract after browser review. GROUP
is leader-first, and unsupported scope or period selections no longer add
visible helper text that changes the action-row layout.

2026-07-29: Revised the detail route after browser review. Removed duplicated
dashboard framing so the frozen report headline and document body lead the
screen, while view and revision controls remain available as reader tools.

2026-07-29: Revised narrative quality after product review. Prompt v5 and the
reader hierarchy now optimize for decision-ready specificity while preserving
the frozen evidence, privacy, lifecycle, and schema contracts.
