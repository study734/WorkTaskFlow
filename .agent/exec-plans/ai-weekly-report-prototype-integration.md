# Integrate the AI weekly report prototype into the current product

This ExecPlan is a living document. Keep `Progress`, `Surprises & Discoveries`,
`Decision Log`, and `Outcomes & Retrospective` current while work proceeds.
This plan follows `.agent/PLANS.md`.


## Purpose / Big Picture

After this work, a paid TEAM group's LEADER can generate, review, revise, and
finalize an evidence-backed weekly AI report without replacing the existing
dashboard or basic report flow. An authorized viewer can open the finalized
report on a dedicated detail page and download a real `.pdf` file. The report
clearly separates server-confirmed metrics and risks from AI-written
interpretation and recommendations.

The standalone prototype under
`source-reference:worktaskflow-ai-weekly-report-final/` is a product-design
reference, not a replacement application. Its useful information hierarchy is
reimplemented with the current React application, Spring Boot authorization,
stored report revisions, privacy boundary, and bilingual design system.


## Progress

- [x] (2026-07-28 11:00+09:00) Inspected the current report implementation with
  Serena at the component, application service, contract, and metrics-source
  symbol levels.
- [x] (2026-07-28 11:00+09:00) Inspected the standalone prototype and the
  current manual-test application in Chrome DevTools at desktop and mobile
  viewports.
- [x] (2026-07-28 11:00+09:00) Confirmed that the current print page requests
  HTML and report JSON but no downloadable PDF response.
- [x] (2026-07-28 11:00+09:00) Classified prototype ideas into existing-contract
  presentation work, new backend work, deferred team-design work, and rejected
  concepts.
- [x] (2026-07-28 20:10+09:00) Protected and validated the current
  uncommitted WIP: 128 backend tests, the frontend production build, and three
  Chrome E2E tests passed before structural changes.
- [x] (2026-07-28 21:00+09:00) Separated the compact dashboard report surface
  from the full report detail surface.
- [x] (2026-07-28 21:00+09:00) Presented BASELINE, server-confirmed risks, AI
  candidates, and report density using the existing API contract.
- [x] (2026-07-28 21:00+09:00) Implemented real basic-report and
  finalized-AI-report PDF attachment downloads.
- [ ] Milestone 4 deterministic previous-recommendation and risk-effectiveness
  assessment is explicitly deferred to a separate schema/domain task.
- [ ] Authorized real-OpenAI validation remains separate; deterministic
  provider, backend, migration, browser, download, and review evidence is
  recorded in `devLog/2026-07-28.md`.


## Surprises & Discoveries

- Observation: the current WIP already has stronger lifecycle behavior than the
  standalone prototype: stored revisions, frozen snapshots, draft editing,
  optimistic editor versions, finalization, superseding, retry, and strict
  structured-output validation.
  Evidence: `WeeklyReportGenerationModule.generate`, `regenerate`, and
  `finalizeReport`; `AiWeeklyReportPanel`.

- Observation: the full Maven test command exceeded the shell tool's 60-second
  response deadline, but Maven completed normally at 58.823 seconds and printed
  `Tests run: 128, Failures: 0, Errors: 0, Skipped: 0` followed by
  `BUILD SUCCESS`. Treat the Maven result, not the wrapper's timeout status, as
  the baseline evidence.

- Observation: much of the prototype's desired data is already returned by the
  current API but is not fully presented. The live response already contains
  `comparison.available`, `metrics.riskSignals`, `onTimeRatePercent`,
  `averageCompletionHours`, member/task references, `leaderDecisions`, and
  evidence values.
  Evidence: the manual environment's
  `GET /api/v1/groups/1/reports/ai-weekly` response and
  `frontend/src/api/reportApi.ts`.

- Observation: the current UI hides the meaning of
  `comparison.available=false`; it simply omits the comparison section. The
  standalone prototype's explicit BASELINE banner communicates the state more
  accurately.
  Evidence: `AiReportContent` only renders comparison content when
  `report.comparison.available` is true.

- Observation: `metrics.riskSignals` is typed in the frontend but is not
  rendered by `AiReportContent`. The current risk section shows only
  AI-generated `analysis.risks`.

- Observation: placing the full report inside the existing dashboard is too
  dense. At a 390-by-844 mobile viewport, the prototype was approximately
  7,485 CSS pixels tall and the current dashboard with its AI report expanded
  was approximately 8,746 CSS pixels tall. The current AI report document alone
  was approximately 4,158 CSS pixels tall.

- Observation: the prototype's `SUMMARY` mode was still approximately 5,147
  CSS pixels tall on mobile. A density switch cannot replace proper page
  responsibility separation.

- Observation: the prototype and current print page both ultimately rely on
  browser printing. Chrome DevTools showed no `application/pdf` request or
  response when opening the current print route.

- Observation: the current application has stronger accessibility and
  navigation primitives than the prototype, including skip links, page
  announcements, bilingual labels, and product navigation. The prototype
  application shell and CSS should not replace them.


## Decision Log

- Decision: Keep the current WIP as the implementation source of truth.
  Reimplement selected prototype concepts rather than copying its application
  shell, TypeScript dataset, or stylesheet.
  Rationale: the WIP owns real authorization, persistence, lifecycle, privacy,
  and API behavior.
  Date/Author: 2026-07-28 / Codex

- Decision: Keep the existing dashboard and basic-report experience. The
  dashboard receives a compact AI report status and summary surface; the full
  report moves to a dedicated detail route.
  Rationale: both inspected implementations are too long when fully embedded in
  the dashboard, especially on mobile.
  Date/Author: 2026-07-28 / Codex

- Decision: Use existing API fields before adding backend schema. BASELINE,
  server risk signals, the executive summary, known metrics, evidence, and
  leader questions can be presented without a new OpenAI call or migration.
  Rationale: this is the narrowest observable improvement and avoids storing
  duplicate derived data.
  Date/Author: 2026-07-28 / Codex

- Decision: Preserve the current stricter OpenAI privacy policy. Task titles,
  descriptions, comments, names, and free-text blocker reasons remain outside
  the provider payload. Human-readable references are resolved on the server
  after AI output validation.
  Rationale: the standalone prototype is display data, not an approved provider
  payload contract.
  Date/Author: 2026-07-28 / Codex

- Decision: Do not implement a numeric health score until the team approves a
  deterministic formula and thresholds. Continue using
  `ON_TRACK | NEEDS_ATTENTION | AT_RISK` and confidence.
  Rationale: the prototype's `68/100` is sample content, not a server policy.
  Date/Author: 2026-07-28 / Codex

- Decision: Treat browser print as an optional secondary action, not successful
  PDF delivery. Product completion requires an attachment response and a
  browser download assertion.
  Rationale: popup/print behavior has already failed the real download
  requirement.
  Date/Author: 2026-07-28 / Codex

- Decision: Defer landing-page AI preview work and final visual polish until the
  generation, finalized-detail, and real-download path pass end-to-end.
  Rationale: the core paid report promise must be reliable before marketing it.
  Date/Author: 2026-07-28 / Codex

- Decision: Stop this implementation after Milestones 1–3. Defer Milestone 4
  longitudinal assessment because it requires a new frozen schema/domain
  contract, and defer Milestone 5 visual/public-preview work to the team.
  Rationale: the user froze scope after the core report and download path; these
  milestones are independent follow-up work, not completion blockers here.
  Date/Author: 2026-07-28 / Codex


## Outcomes & Retrospective

Planning outcome: the next implementation should not begin with a broad backend
rewrite. The first product increment is a route and presentation refactor that
uses data already in the report response. Real PDF delivery is the next
independent gate. Longitudinal risk and recommendation assessment is valuable
but follows only after those two increments are stable.

Implementation outcome: Milestones 1–3 now provide the compact dashboard,
dedicated density-aware detail route, evidence-backed report content, and
server-generated basic/finalized-AI PDF downloads. Report infrastructure reads
task history through a task-application query contract, and basic PDF rendering
occurs outside the short locked download-record transaction. Milestones 4 and
5 remain explicitly deferred. Deterministic local validation does not establish
real OpenAI provider behavior.


## Context and Orientation

`frontend/src/features/dashboard/pages/GroupDashboardPage.tsx` owns the current
group dashboard and the basic-versus-AI report tabs. It currently renders the
full `AiWeeklyReportPanel` below all dashboard panels.

`frontend/src/features/report/components/AiWeeklyReportPanel.tsx` owns report
loading, revision selection, generation, failed-attempt retry, draft editing,
regeneration, finalization, and the link to the print route. It also renders the
entire report content, which gives it too many page-level responsibilities.

`frontend/src/features/report/components/AiReportContent.tsx` renders the stored
report metrics, operations, narrative, evidence links, and publication status.
It is reusable on a detail page and in a PDF renderer, but it currently has no
display-density contract and does not render a BASELINE state or the
server-confirmed `metrics.riskSignals`.

`frontend/src/features/report/pages/AiWeeklyReportPrintPage.tsx` loads a stored
report and calls `window.print()`. It does not download a file.

`frontend/src/api/reportApi.ts` defines the live report contract and generation,
revision, edit, regeneration, and finalization requests. Prefer extending this
contract additively.

`backend/src/main/java/com/teamproject/report/application/WeeklyReportGenerationModule.java`
owns frozen snapshot generation, retry/revision behavior, provider calls, draft
editing, finalization, and views. A failed generation must not replace a
successful finalized report.

`backend/src/main/java/com/teamproject/report/application/ReportContracts.java`
defines the stored metrics, comparison, AI context, evidence, narrative,
operational view, and API view records.

`backend/src/main/java/com/teamproject/report/infrastructure/TaskMetricsSnapshotSource.java`
computes the current and previous periods, member/task aliases, deterministic
risk signals, evidence, and AI-safe context. Its current server risk rules are
overdue present, on-hold present, and high-priority present.

`backend/src/main/java/com/teamproject/report/application/NarrativeContract.java`
defines and validates the OpenAI structured-output schema. It enforces evidence
keys and allowed task, objective, and member aliases.

`frontend/e2e/ai-weekly-report.chrome.spec.ts` and
`docs/ai-weekly-report-manual-test.md` are the current isolated test contract.

The standalone reference's important files are:

- `source-reference:worktaskflow-ai-weekly-report-final/src/app.ts`
- `source-reference:worktaskflow-ai-weekly-report-final/style.css`
- `source-reference:worktaskflow-ai-weekly-report-final/preview.png`
- `source-reference:worktaskflow-ai-weekly-report-final/README.md`

The reference contributes information hierarchy, BASELINE messaging,
server-risk versus AI-candidate labeling, meeting questions, report-density
ideas, and follow-up concepts. It does not contribute production API,
authorization, persistence, or actual PDF delivery.

Repository workflow constraints:

- Work and intermediate commits stay on `member1-work` and the `personal`
  remote.
- Preserve all unrelated existing changes. Do not reset, stash over, or
  overwrite the current WIP.
- Do not push to the team `origin`.
- Final submission is rebuilt from the latest `origin/main` on `member1`, with
  personal files such as `AGENTS.md` and `.agent/` excluded.


## Plan of Work

Start by validating and reviewing the current WIP without adding prototype
changes. Record a clean test baseline and split any future commits by coherent
behavior rather than committing the whole dirty tree as one unit.

Next, separate report page responsibilities. Keep a compact report controller
inside the dashboard, add a dedicated finalized/draft report detail route, and
reuse the full report renderer there. The compact dashboard surface shows the
selected week, publication/generation state, revision, headline, one primary
action, and buttons to generate or open the report. It does not render all
tasks, members, evidence, or narrative sections.

Then use the existing API contract to implement explicit BASELINE messaging,
an executive summary, additional server metrics, server-confirmed risk cards,
AI risk candidates, decision questions, and density modes on the detail page.
No backend schema change is required for this milestone.

After the page split is stable, add real PDF endpoints for both the basic report
and a stored AI report. Validate the chosen Java renderer before adopting it.
The endpoint must render from a stored frozen snapshot and narrative, not call
OpenAI during download.

Finally, add deterministic longitudinal assessment for the prototype's
"previous recommendation outcome" and "risk management effect" concepts.
These calculations compare the previous finalized report's references and
server risk codes with the current activity snapshot. Store the calculated
assessment with the new report so that later downloads remain reproducible.


## Milestones


### Milestone 0 — Protect and validate the current WIP

Goal: establish a trustworthy starting point without modifying or losing
existing work.

Inspect `git status`, the report/task/migration diff, and untracked test assets.
Run the narrow report tests first, then the applicable full backend and
frontend gates. Review the report changes separately for permissions, privacy,
provider failure, revision conflicts, and schema compatibility.

Acceptance: the current report lifecycle is understood, test results are
recorded, and no unrelated file has been staged, reverted, or overwritten.


### Milestone 1 — Separate dashboard summary and report detail

Goal: retain the main dashboard while preventing the report from turning it
into an 8,000-pixel mobile page.

In `GroupDashboardPage.tsx`, retain the basic/AI tabs but render a compact AI
report summary surface. Refactor shared fetching and report actions from
`AiWeeklyReportPanel.tsx` into a small report-resource hook or controller module
so both the compact surface and detail page use one behavior contract.

Add a route such as:

    /groups/:groupId/reports/ai-weekly/:reportId

Create `AiWeeklyReportDetailPage.tsx` for revision selection, draft editing,
regeneration, finalization, and full `AiReportContent`. Keep the print route
render-only.

Acceptance:

- The existing dashboard panels and basic report flow remain unchanged.
- The mobile dashboard does not embed the full AI report document.
- Opening the latest report navigates to a dedicated detail page.
- Refreshing the detail route reloads the same stored revision.
- Generate, edit, regenerate, and finalize behavior still uses the existing API
  and editor-version conflict rules.


### Milestone 2 — Apply the prototype hierarchy using existing data

Goal: deliver the prototype's clearest product ideas without adding provider
cost, database columns, or invented metrics.

On the detail page, render in this order:

1. Publication status, week, generation time, revision, and confidence.
2. A "30-second key judgement" using the stored headline and summary.
3. The first ordered action and first leader decision as quick-focus items.
4. Explicit comparison or BASELINE status.
5. Server metrics: tasks, completion, overdue, on hold, on-time rate, checklist,
   and average completion time.
6. Server-confirmed risks from `metrics.riskSignals`.
7. AI-written risk candidates from `analysis.risks`.
8. Achievements, changes, next actions, and leader decisions.
9. Member/task evidence and data limitations.

Add `SUMMARY | STANDARD | DETAILED` as a local display preference, preferably a
shareable query parameter. It must not create a report revision or call OpenAI.
Summary hides task/member drilldowns; standard shows the primary report;
detailed includes all operations and evidence.

Do not add a numeric health score. Do not add unassigned-work or throughput
metrics until their period semantics are computed and tested on the server.

Acceptance:

- `comparison.available=false` visibly says that this report is the initial
  baseline and does not display fake deltas.
- Server risks and AI candidates have different labels and explanatory copy.
- All displayed numeric values map to `metrics`, `comparison`, or `evidence`.
- Switching density causes no network request.
- Evidence remains collapsed by default and keyboard accessible.


### Milestone 3 — Deliver real downloadable PDFs

Goal: make "PDF download" produce a real file for free/basic and paid/finalized
reports.

Before adding a dependency, validate the candidate renderer on Java 21 for:

- Korean glyphs with an explicitly packaged or configured font;
- multipage tables and non-breaking cards;
- print colors and links;
- memory use on a detailed report;
- license and dependency vulnerability status.

Prefer a backend interface such as `WeeklyReportPdfRenderer` so the application
service is not coupled to one rendering library. Add an authenticated endpoint
that returns:

    Content-Type: application/pdf
    Content-Disposition: attachment; filename="..."

The AI PDF endpoint loads the authorized stored report by ID and renders its
frozen metrics, evidence, operational view, and stored narrative. It must never
call OpenAI. A failed provider or disabled AI configuration must not affect the
basic PDF endpoint.

Only a finalized report is a shared team artifact. A LEADER may receive a
clearly marked draft preview if the team chooses to keep draft PDF support;
other members must not receive a draft as if it were final.

Acceptance:

- Playwright observes a browser `download` event.
- The saved file has a `.pdf` name, begins with `%PDF-`, and has nonzero size.
- Korean text is extractable or visually verified without replacement glyphs.
- Downloading the same report twice does not call OpenAI and yields the same
  report content.
- The basic PDF still downloads when AI is unconfigured or provider generation
  fails.


### Milestone 4 — Add deterministic follow-up and risk-effectiveness assessment

Goal: implement the prototype's most valuable new backend concept only after
the core report path is stable.

Compare the previous finalized report with the current frozen snapshot:

- A previous server risk code absent from the current snapshot is a resolved
  candidate.
- A risk still present with an improved evidence value is partially effective.
- A referenced task with no qualifying activity is not executed.
- Missing or partial tracking history yields `UNKNOWN`, not a success or
  failure claim.

For previous top actions, use stored task/objective references and activity
events. Do not match by free-text action wording. Store the calculated
assessment in the new report's frozen JSON under an additive schema version so
reopening or downloading the report is reproducible.

AI may explain the server assessment and ask follow-up questions. It may not
invent the outcome or percentage.

Acceptance:

- Unit tests cover resolved, partially effective, not executed, and unknown.
- A failed regeneration keeps the current finalized report.
- Older v3/v4 reports remain readable.
- The new report displays the prior report ID/revision used for comparison.


### Milestone 5 — Team frontend review and public preview

Goal: let the team decide final visual language after the product contract is
stable.

Review the detail hierarchy, density control, risk labels, member information,
and mobile screenshots with the frontend team. Reuse current product tokens and
components. Do not copy the reference sidebar or stylesheet wholesale.

Only after the real generation/detail/download E2E passes, create a separate
landing-page task for a fixed anonymous AI report preview. The public preview
does not call OpenAI and clearly says the paid feature is a preview when it is
not yet generally available.

Acceptance: team decisions are recorded, desktop/mobile visual regression
evidence exists, and the landing work is not mixed into the core report diff.


## Concrete Steps

Run commands from the repository root unless a command changes directory.

Inspect the preserved worktree:

    rtk git status
    rtk git diff --name-only
    rtk git diff

Run narrow backend report tests:

    cd backend
    .\mvnw.cmd -q "-Dtest=WeeklyReportModuleTest,WeeklyReportLifecycleTest,WeeklyReportApiTest,TaskActivityMetricsSnapshotSourceTest,NarrativeContractTest,OpenAiResponsesNarrativeAdapterTest" test

Run the full backend gate:

    .\mvnw.cmd test

When Docker Desktop is available, validate all MySQL migrations from V1:

    .\mvnw.cmd -q "-Dtest=MySqlFlywayMigrationTest" test

Run frontend build and isolated Chrome E2E:

    cd ..\frontend
    npm.cmd run build
    npm.cmd run test:e2e

Extend the E2E suite with separate scenarios for:

- dashboard summary to report-detail navigation;
- BASELINE and comparison modes;
- server risk versus AI risk labeling;
- density switching with zero generation requests;
- LEADER draft/edit/regenerate/finalize;
- MEMBER finalized-view/download authorization;
- actual basic and AI PDF downloads;
- provider failure with basic PDF still available.

Use the manual fixture after automated tests:

    .\scripts\ai-report-manual-test.ps1 reset
    .\scripts\ai-report-manual-test.ps1 start -OpenBrowser
    .\scripts\ai-report-manual-test.ps1 status

The deterministic fixture proves product flow, not real OpenAI behavior. Run an
authorized real-key test separately for Responses API output quality, refusal,
timeout, malformed result, and cost recording. Never commit the key or print it
in logs.

Before completion, review the final diff independently:

    rtk git diff --check
    rtk git diff --name-only
    rtk git diff


## Validation and Acceptance

The feature is complete only when all applicable outcomes are true:

- A FREE/basic report downloads as a real PDF without OpenAI.
- A PAID TEAM LEADER can generate, edit, regenerate, and finalize a weekly AI
  report.
- An authorized member can open and download the finalized shared report but
  cannot generate, edit, regenerate, or finalize it.
- The dashboard keeps its current task, schedule, workload, risk, and basic
  report behavior and shows only a compact AI report surface.
- The full report has a dedicated refreshable URL.
- BASELINE, partial history, server risks, AI candidates, and AI
  recommendations are not confused with one another.
- All numeric values and references are traceable to frozen server evidence.
- Display-density and member-view controls do not cause an OpenAI call.
- Provider failure does not replace an existing finalized report or block the
  basic PDF.
- Actual PDF download passes automated file-level and visual checks.
- Real OpenAI behavior is not claimed from the fake provider.
- The final diff contains only intended product files and excludes secrets,
  personal files, `.agent/`, and unrelated changes.


## Idempotence and Recovery

The page split is additive: keep the existing report renderer and API behavior
working while introducing the detail route, then remove only the redundant
embedded rendering after E2E passes.

PDF generation is read-only with respect to the report. Retrying a download is
safe and must not create a report revision or provider call.

Schema changes are additive. Keep readers for v3/v4 data and add a new schema
reader only when longitudinal assessment is implemented. Do not rewrite stored
historical report JSON in place.

Failed generation or regeneration keeps the existing finalized report. Existing
row locking, unique revision constraints, generation lease, and editor-version
checks remain authoritative.

If a PDF library spike fails Java 21, Korean, security, or layout validation,
remove the spike dependency before selecting another renderer. Do not leave two
production PDF libraries in the application.

Do not use destructive Git cleanup. Preserve the dirty worktree, stage files
explicitly, and keep intermediate commits on `member1-work`.


## Interfaces and Dependencies

Expected frontend interfaces:

- A compact dashboard report component that does not render full report
  sections.
- `AiWeeklyReportDetailPage` for lifecycle actions and complete report content.
- A shared report resource/controller hook for loading, revisions, generation,
  editing, regeneration, and finalization.
- An additive `density` input for report rendering:
  `SUMMARY | STANDARD | DETAILED`.

Expected backend interfaces for PDF work:

- An application-facing `WeeklyReportPdfRenderer`.
- A basic-report PDF renderer or a shared report-document model where
  appropriate.
- Authenticated attachment endpoints that use existing report authorization.

Expected additive contracts for the later longitudinal milestone:

- A server-defined outcome enum such as
  `RESOLVED | PARTIAL | NOT_EXECUTED | NO_EFFECT | UNKNOWN`.
- Previous report ID and revision.
- Evidence-backed risk and recommendation follow-up items stored in the new
  frozen report.

Resolve current documentation for the chosen PDF library with Context7 during
implementation. Do not select library APIs from memory.


## Artifacts and Notes

Chrome DevTools planning evidence from 2026-07-28:

- Standalone prototype desktop body height: approximately 4,181 CSS pixels.
- Standalone prototype mobile body height: approximately 7,485 CSS pixels.
- Standalone prototype mobile summary mode: approximately 5,147 CSS pixels.
- Current mobile dashboard with AI report expanded: approximately 8,746 CSS
  pixels.
- Current mobile AI report document: approximately 4,158 CSS pixels.
- Current print route network: HTML document plus report JSON; no PDF response.

These measurements are diagnostic observations, not pixel-perfect acceptance
thresholds. Their purpose is to justify page responsibility separation.


## Revision Note

2026-07-28: Replaced the earlier backend-heavy integration order after live
Chrome DevTools and Serena inspection. The current API already exposes most
prototype data, while both current and reference UIs are too dense when embedded
in the dashboard. The revised order is: protect WIP, split dashboard/detail
responsibilities, expose existing data, deliver real PDF, then add
longitudinal assessment.
