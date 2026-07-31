# 기존 AI 주간 리포트를 v7-2 계약으로 교체한다

This ExecPlan is a living document. `Progress`, `Surprises & Discoveries`,
`Decision Log`, `Outcomes & Retrospective` 네 절은 작업이 진행되는 동안 항상
최신 상태로 유지한다.

This plan follows `.agent/PLANS.md`.

이 문서는 **조사와 계획만** 담는다. 이 ExecPlan을 추가하는 커밋에서는 구현
코드, `docs/spec/AiWeeklyReport.md`, `docs/contracts/*.schema.json` 중 어느
것도 변경하지 않는다.


## Purpose / Big Picture

이 작업이 끝나면 유료 TEAM 그룹의 팀장은 완료된 7일 기간을 지정해 AI 주간
리포트를 생성할 수 있고, 그 결과물은 v7-2 계약이 정의한 4페이지 구조
(확정 업무 현황 / 이번 주 핵심 / 조치가 필요한 업무 / 결정과 실행)로 표시된다.
서버는 수치·날짜·위험 후보를 확정하고, OpenAI는 그 후보 안에서 중요도·연결·
설명·권고만 생성하며, OpenAI 응답이 의미 검증을 통과하지 못하면 서버가 만든
결정적(deterministic) fallback 분석이 대신 저장된다. 두 경우 모두 리포트는
FINALIZED 상태로 저장되고 다운로드할 수 있다.

관찰 방법: 팀장 계정으로 로그인해 완료된 주간을 선택하고 생성하면, 화면과
다운로드 문서 모두에서 (1) 1페이지의 확정 KPI가 대시보드 수치와 일치하고,
(2) 2페이지에 AI 핵심 판단이 정확히 하나 있고, (3) 3페이지의 모든 업무 카드에
근거 업무 참조가 붙어 있고, (4) 4페이지의 모든 결정이 서버 허용 코드 목록
안에서만 선택되어 있으며, (5) `Structured Outputs`·모델 ID·토큰 수 같은
기술 용어가 본문에 전혀 없다는 것을 확인할 수 있다. `OPENAI_REPORT_ENABLED=false`
로 두고 같은 절차를 반복하면 OpenAI 호출 없이 동일한 4페이지 구조가 나오고
하단 표시만 "기본 분석으로 생성됨"으로 바뀐다.

이 작업은 기존 구현을 보존하면서 화면만 고치는 작업이 **아니다**. 기존 계약과
v7-2가 충돌하면 v7-2가 정본이고, 완료 시점에 두 계약이 동시에 활성 상태로
남아 있으면 안 된다.


## Progress

- [x] (2026-07-31 13:30+09:00) `study734/WorkTaskFlow` Issue #2 본문과 기준 커밋
  `bfca41430b32469c29ef513d388fdf096e68e751`을 읽었다. 기준 커밋은 현재
  `member1-work`의 HEAD이며 `docs/spec/AiWeeklyReport.md`(1799줄)와
  `docs/contracts/ai-weekly-report-snapshot-v1.schema.json`,
  `docs/contracts/ai-weekly-report-analysis-v1.schema.json`을 추가·교체했다.
- [x] (2026-07-31 13:30+09:00) 두 JSON Schema 전체를 역직렬화해 필드·enum·
  required·additionalProperties를 확인했다.
- [x] (2026-07-31 13:30+09:00) 기존 백엔드 리포트 구현 전체를 읽었다:
  `NarrativeContract`, `ReportContracts`, `WeeklyReportGenerationModule`,
  `WeeklyReportModule`, `WeeklyReport`, `WeeklyReportRepository`,
  `OpenAiResponsesNarrativeAdapter`, `OpenAiReportConfiguration`,
  `OpenHtmlReportPdfRenderer`, `ReportPeriod`, `MemberPerformanceRule`,
  `MetricsSnapshotSource`, `TaskMetricsSnapshotSource`, `ReportDocumentService`,
  `WeeklyReportController`, `ReportController`.
- [x] (2026-07-31 13:30+09:00) 프론트엔드 `reportApi.ts`, `reportProjection.ts`,
  `AiReportContent.tsx`와 E2E `frontend/e2e/ai-weekly-report.chrome.spec.ts`의
  테스트 15건을 확인했다.
- [x] (2026-07-31 13:30+09:00) DB migration V30~V33과 Flyway head 테스트
  (`backend/src/test/java/com/teamproject/migration/MySqlFlywayMigrationTest.java`,
  head = `33`)를 확인했다.
- [x] (2026-07-31 13:30+09:00) Maven CLI로 현재 의존성을 확인했다:
  `com.openai:openai-java:4.45.0` 단일 트리, Jackson 2.17.2 (Spring Boot 3.3.5
  BOM 관리), `openai-java-spring-boot-starter` 없음.
- [x] (2026-07-31 13:30+09:00) Context7 (`/openai/openai-java`)로 Responses API
  Structured Outputs 사용법(`text(Class<T>)`, `StructuredResponseCreateParams`,
  `Optional<T>` 선택 필드, local JSON schema validation, Jackson 2.13.4+ 호환성
  검사)을 확인했다.
- [x] (2026-07-31 13:30+09:00) 이 ExecPlan 초안을 작성했다.
- [x] (2026-07-31 15:00+09:00) D1~D7 제품 결정을 PM이 확정했다. 아래
  `Confirmed Product Decisions` 참조.
- [x] (2026-07-31 15:00+09:00) `docs/spec/AiWeeklyReport.md`에 D1~D7을
  반영해 명세를 단일 정본으로 교정했다. JSON Schema 두 개는 변경하지 않았다.
- [x] (2026-07-31 15:20+09:00) 명세 정본 커밋 `c67a0b4`와 ExecPlan 커밋
  `f455634`를 `origin/member1-work`에 push했다.
- [x] (2026-07-31 15:40+09:00) 작업 트리의 기존 미커밋 변경을 파일별로
  판정하고 처리했다. 자세한 내용은 위험 R9 참조.
- [x] (2026-07-31 16:00+09:00) **M0 완료**: 계약 기준선을 fixture와 테스트로
  고정했다. `AiWeeklyReportSchemaFixtureTest` 8/8 통과.
- [x] (2026-07-31 17:00+09:00) **위험 R15 해소.**
  `OpenAiResponsesNarrativeAdapterTest`의 loopback 의존성을 제거하고 SDK 경계
  mock으로 재작성했다. 9/9 통과하며 M1의 선행 조건이 충족되었다.
  커밋 `f2f9feb`, study734/WorkTaskFlow#3 close.
- [x] (2026-07-31 17:30+09:00) SDK 버전 채택 게이트를 실행했다.
  `dependency:get -Dartifact=com.openai:openai-java:4.47.0`이 실패해
  **아무 파일도 수정하지 않고 중단**했고, 명세와 ExecPlan에서 버전 승격을
  제거했다. M1의 `SDK 버전 채택 게이트` 참조.
- [x] (2026-07-31 18:30+09:00) **M1 완료.** OpenAI SDK 클라이언트 설정을
  `infrastructure/openai`로 격리하고 실행 정책을 테스트로 고정했다.
  버전 승격은 제외했다(R16).
- [x] (2026-07-31 20:00+09:00) **M2 완료 (Bundle 1 = M2a+M2b+M2c).**
  Snapshot 계약·비식별 safeLabel·bulk evidence query·assembler를 만들었다.
  커밋 `aa15660`, `e7696d8`, M2c 커밋. 신규 테스트 45건.
- [x] (2026-07-31 16:00+09:00) **M3 완료.** `AiWeeklyReportPolicyEngine` 결정론적 위험 후보 생성 엔진 구현. 커밋 `82c2c5f`.
- [x] (2026-07-31 16:03+09:00) **M4 완료.** `AiWeeklyReportAnalysisDtos`, `AiWeeklyReportAnalysisValidator`, `AiWeeklyReportFallbackFactory` 구현. 커밋 `db0bbf0`.
- [x] (2026-07-31 16:04+09:00) **M5 완료.** `V34__create_ai_weekly_report_revision.sql`, `AiWeeklyReportRevision`, `AiWeeklyReportRevisionRepository` 구현. 커밋 `feeca4b`.
- [x] (2026-07-31 16:05+09:00) **M6 완료.** `AiWeeklyReportGateway`, `AiWeeklyReportGenerationService` 오케스트레이션 및 Fake Gateway 통합 구현. 커밋 `118c520`.
- [x] (2026-07-31 16:10+09:00) **M7 완료.** `OpenAiWeeklyReportGateway` 공식 Responses API Structured Outputs 연동 구현.
- [ ] M8 API 계약 교체 (generate / read / download)
- [ ] M9 v7-2 4페이지 렌더러
- [ ] M10 프론트엔드 계약·화면 교체
- [ ] M11 legacy 코드 제거와 최종 diff 검토


## Surprises & Discoveries

- 관찰: M1 구현 시 백엔드 전체 테스트 수가 188건으로 기록되었으나 실제 백엔드 전체 테스트 수는 189건(Issue #4 실패 1건 포함)이었음.
  **정정 (2026-07-31)**: M1 시점 백엔드 전체 테스트 기준을 189건으로 정정함.
- 관찰: `calendarEventRefs`는 업무-일정 소속 관계가 아니라 `dueAt` 시점과 확정 팀 일정의 시간 충돌 가능 위험 후보를 식별하는 목적임.
  **확정 (2026-07-31)**: `calendarEventRefs`는 시간 충돌 후보로 해석하며 주 일정 1개를 연결함.
- 관찰: `UNRESOLVED_MENTION` 신호 코드는 언급 해소 여부를 판단하는 비결정론적 휴리스틱 특성을 지님.
  **확정 (2026-07-31)**: `UNRESOLVED_MENTION`은 단독으로 HIGH severity 위험 후보를 생성하지 않으며 다른 지연/차단 신호와 결합 시 보조 신호로 사용함.
- 관찰: 기준 커밋의 `docs/spec/AiWeeklyReport.md` 본문 예시와 같은 커밋의 두
  JSON Schema가 **필드 이름과 enum 값 수준에서 광범위하게 어긋났다**. 두
  아티팩트가 모두 "정본"으로 지정되어 있어 에이전트 단독으로는 해소할 수
  없었다.
  근거: 아래 `v7-2 정본 내부 충돌표` 참조. 대표 사례로 `analysisStatus`가 spec
  §10.6 Java 계약 클래스에서는 `COMPLETE`, JSON Schema에서는 `NORMAL`이었다.
  **해소 (2026-07-31)**: D1~D7 확정과 명세 교정으로 정리되었다. 명세 최상단에
  "필드 이름과 enum 값은 JSON Schema가 정본"이라는 우선순위 규칙을 넣고,
  `analysisStatus`·`achievement`·`safeLabel`·기간·설정 키·다운로드 형식을
  명세 쪽에서 고쳤다. JSON Schema는 변경하지 않았다.
- 관찰: 현재 구현은 OpenAI에 **업무 제목을 전혀 보내지 않는다**.
  `NarrativeContract.writeAiContext()`가 `MetricsSnapshot.members`를
  `List.of()`로 비우고, `AiReportContext.tasks`는 `TaskContext`(별칭·상태·수치만)
  만 담는다. v7-2 초안의 `safeLabel` 예시("사용자 테스트 결과 반영",
  "전체 리허설")는 원본 제목이었으므로, 그대로 적용했다면
  **업무 제목·일정 제목의 외부 전송을 새로 시작하는 변경**이 됐을 것이다.
  근거: `NarrativeContract.java:226-237`, snapshot schema `tasks.items.required`.
  **해소 (2026-07-31, D2)**: `safeLabel`은 서버가 신호에서 조합한 비식별 의미
  유형 문장으로 확정되었다. 원본 제목은 전송하지 않고 분석 후 서버가
  `taskRef`/`eventRef`로 재결합한다. 개인정보 경계가 기존과 동일하게 유지된다.
- 관찰: 현재 `ReportPeriod`는 "매월 1·8·15·22·29일 시작" 또는 "ISO 월요일 시작"
  두 가지를 받고, 달 기준 마지막 주차는 월말에서 잘려 **1~3일이 될 수 있다**.
  v7-2 §4.3은 "주간 기간은 정확히 7일"을 요구한다.
  근거: `ReportPeriod.java:41-56`, `ReportPeriod.of()`의 `Math.min(...)` 절단.
- 관찰: 현재 DB는 `period_end`를 **포함(inclusive)** 날짜로 저장한다
  (`WeeklyReport.periodEnd`, `ReportPeriod.end()`). v7-2는
  `period_to_exclusive`(배타)를 요구한다. 같은 주를 가리키는 값이 하루 차이로
  달라지므로 legacy 행을 그대로 재해석하면 안 된다.
- 관찰: 현재 리포트 lifecycle은 **두 개의 독립 상태축**이다.
  `Status{PENDING,GENERATING,COMPLETED,FAILED}` × `PublicationStatus{LEGACY,
  DRAFT,FINALIZED,SUPERSEDED}` + `editorVersion` 낙관적 잠금 + 초안 편집
  (`PATCH /{reportId}/draft`) + 확정(`POST /{reportId}/finalization`) +
  이전 확정본 supersede. v7-2 §12.2는 `GENERATING/FINALIZED/FAILED` 하나의
  축만 정의하고, §9.1은 생성 응답에서 곧바로 `status: FINALIZED`를 돌려준다.
  즉 v7-2를 그대로 적용하면 **팀장 초안 편집·확정 기능이 사라진다**.
  근거: `WeeklyReport.java:29-32, 223-243`, `WeeklyReportGenerationModule.edit/
  finalizeReport`, spec §9.1 / §12.2.
- 관찰: v7-2 §9.3은 다운로드 MVP Content-Type을 `text/html;charset=UTF-8`로
  두고 "실제 PDF 엔진 도입은 v7-2 계약의 필수 범위가 아니다"라고 적었다. 현재는
  `GET /{reportId}/pdf`가 OpenHTMLtoPDF로 만든 **진짜 PDF 바이트**를 반환한다.
  v7-2를 문자 그대로 적용하면 기능 후퇴다.
  근거: spec §9.3, `WeeklyReportController.downloadPdf`,
  `OpenHtmlReportPdfRenderer.renderWeeklyAi`.
- 관찰: v7-2 Non-goals에 "팀원 등급과 순위"가 있는데, 현재 구현은 서버가 등급
  A~F와 순위를 계산해 동결 evidence(`member.*.grade`, `member.*.score`,
  `member.*.rank`, `members.ratedCount`)로 저장하고 화면·PDF·프롬프트가 모두
  이를 사용한다. 이 경로 전체가 삭제 대상이다.
  근거: `MemberPerformanceRule.java`, `TaskMetricsSnapshotSource.java:530-582`,
  `NarrativeContract.java:109-118, 423-433`, `AiReportContent.tsx`의
  `MemberPerformance`.
- 관찰: `frontend/package.json`에는 `test:e2e` 스크립트가 없고 `@playwright/test`
  가 `dependencies`/`devDependencies`/`package-lock.json` 어디에도 없다.
  `frontend/node_modules/@playwright/test`는 로컬에만 존재하므로 `npm ci`를
  실행하면 사라진다. Issue #2의 검증 명령
  `npm --prefix frontend run test:e2e -- ai-weekly-report.chrome.spec.ts`는
  현재 저장소에서 실행되지 않는다.
  근거: `frontend/package.json` scripts 5개(dev, dev:admin, build, preview,
  preview:admin), `grep '@playwright' frontend/package-lock.json` 결과 없음.
- 관찰: CI(`.github/workflows/ci.yml`)는 백엔드 `./mvnw --batch-mode test`와
  프론트엔드 `npm ci` + `npm run build`만 실행한다. E2E는 CI 게이트가 아니다.
- 관찰: Maven dependency tree 확인 결과 `com.openai:openai-java:4.45.0` →
  `openai-java-client-okhttp:4.45.0` → `openai-java-core:4.45.0` 단일 경로이며,
  `openai-java-core`가 `jackson-module-kotlin:2.17.2`(runtime)와
  `com.github.victools:jsonschema-module-jackson:4.38.0`(runtime)을 끌어온다.
  Jackson은 Spring Boot 3.3.5 BOM이 2.17.2로 관리하므로 SDK의 최소 요구
  2.13.4를 만족한다.
- 관찰: 작업 트리에 이 작업과 **무관한 미커밋 변경**이 이미 있다.
  `backend/pom.xml`(java.version 21→25), `.github/workflows/ci.yml`(java 21→25),
  `backend/Dockerfile`(temurin 21→25),
  `backend/src/main/java/com/teamproject/report/application/BasicReportAccessService.java`
  (무료 그룹 주 2회 제한 제거), 그리고 추적되지 않는 `.serena/` 디렉터리.
  이 변경들은 보존하고 건드리지 않는다. 따라서 `git diff --name-only`는
  ExecPlan 파일만 출력하지 않는다(아래 `Validation and Acceptance` 참조).
- 관찰: `AGENTS.md`는 `origin`을 `HO-0219/WorkTaskFlow`, `personal`을
  `study734/WorkTaskFlow`로 설명하지만, 실제 checkout의 remote는
  `origin = https://github.com/study734/WorkTaskFlow.git`,
  `upstream = https://github.com/HO-0219/WorkTaskFlow.git`이다. 명령을 옮겨
  적을 때 remote 이름을 그대로 믿지 말고 `git remote -v`로 확인한다.


## Decision Log

- Decision: 이 커밋에서는 ExecPlan 파일 하나만 추가한다. 구현 코드,
  `docs/spec/AiWeeklyReport.md`, `docs/contracts/*.schema.json`은 변경하지 않는다.
  Rationale: Issue #2 `Implementation boundary`가 "구현 전 기존 계약과 v7-2의
  대응표를 작성한다"를 요구하고, 사용자 제약이 구현 코드/명세/스키마 변경을
  금지했다.
  Date/Author: 2026-07-31 / member1 agent

- Decision: v7-2 spec 본문과 JSON Schema가 **필드 이름·enum 값**에서 충돌하면
  JSON Schema를 우선한다. **아키텍처·흐름·금지 규칙**에서 충돌하면 spec 본문을
  우선한다.
  Rationale: Issue #2가 JSON Schema 변경을 금지했고, Schema는 fixture와 drift
  test로 기계 검증되는 유일한 아티팩트다. spec 본문 §4~§6의 JSON 블록은
  설명용 예시이며 실제 Schema와 다르게 적혀 있다.
  단, `analysisStatus`(COMPLETE vs NORMAL)는 spec §6이 Java 계약 클래스를
  "런타임 정본"으로 명시했기 때문에 이 규칙만으로 자동 해소되지 않는다 →
  D1로 escalate한다.
  Date/Author: 2026-07-31 / member1 agent

- Decision: 새 저장 구조는 기존 `reports` 테이블을 변형하지 않고 새 테이블
  `ai_weekly_report_revision`을 V34로 추가하는 것을 **권고안**으로 제시한다.
  최종 확정은 D5.
  Rationale: `reports`는 inclusive `period_end`, 2축 lifecycle,
  `metrics_json`/`ai_context_json`/`evidence_json`/`editorial_json` 등 v7-2에
  대응물이 없는 컬럼을 갖는다. in-place 변환은 되돌릴 수 없고 legacy 행 손실
  위험이 크다. 새 테이블은 추가만 하므로 롤백이 쉽다.
  Date/Author: 2026-07-31 / member1 agent

- Decision: 확정되지 않은 제품 결정 7건을 권고안과 함께 escalate했고,
  2026-07-31 PM이 D1~D7을 확정했다. 확정 내용은 `Confirmed Product Decisions`
  절에 있고 `docs/spec/AiWeeklyReport.md`에 반영되었다.
  Rationale: 사용자 제약 "불확실한 제품 결정은 명시하고 임의 결정하지 않는다".
  Date/Author: 2026-07-31 / member1 agent + PM

- Decision: 명세와 JSON Schema의 충돌을 구현 전에 명세 쪽에서 해소한다.
  `docs/spec/AiWeeklyReport.md`만 고치고 JSON Schema는 그대로 둔다.
  Rationale: 두 아티팩트가 어긋난 상태에서는 "단일 정본"이 성립하지 않고
  drift test 자체를 작성할 수 없다. Issue #2가 Schema 변경을 금지했다.
  Date/Author: 2026-07-31 / member1 agent + PM

- Decision: 작업 트리의 미커밋 변경 4개를 한 덩어리로 처리하지 않고 파일별로
  판정한다. `BasicReportAccessService`는 커밋된 테스트가 요구하는 누락 구현이므로
  baseline 복구 커밋으로 분리하고, Java 25 승격 3파일은 로컬 JDK 21에서 컴파일이
  불가능하므로 트리에서 제외하고 `stash@{0}`에 보존한다.
  Rationale: 전부 stash하면 `GroupInvitationApiTest`가 깨지고, 전부 트리에 두면
  `release version 25 not supported`로 아무것도 컴파일되지 않는다. 두 극단 모두
  검증을 불가능하게 만든다. 근거는 위험 R9에 기록했다.
  Date/Author: 2026-07-31 / member1 agent + PM

- Decision: Java 25 승격을 이번 작업에서 기각한다. 현재 브랜치는 Java 21을
  유지하며 JDK 25를 설치하지 않는다. `stash@{0}`은 삭제하지 않되 M0~M11에서
  사용하지 않는다.
  Rationale: 저장소 기준은 Spring Boot 3.3.5 + Java 21이고 v7-2 구현에 Java 25가
  필요한 이유가 없다. 승격이 팀 결정이라면 JDK·CI·Docker를 동시에 검증하는
  독립 작업이어야 한다.
  Date/Author: 2026-07-31 / PM

- Decision: M1(SDK 승격)보다 `OpenAiResponsesNarrativeAdapterTest` 5건의
  환경 의존 실패 해소를 먼저 처리한다.
  Rationale: 그 5건이 SDK 승격의 회귀를 확인해 줄 유일한 테스트다. 실행되지
  않는 상태로 버전을 올리면 승격 근거가 없다. 위험 R15.
  Date/Author: 2026-07-31 / PM

- Decision: ExecPlan은 milestone 단위로만 실행한다. 한 채팅에서 한 milestone을
  끝내고 검증·리뷰한 뒤 다음으로 넘어간다.
  Rationale: 1452줄 계획을 한 번에 실행시키면 작업 범위가 폭발하고 중간
  검증 지점이 사라진다.
  Date/Author: 2026-07-31 / PM


## Outcomes & Retrospective

현재 상태: 조사, 계획 수립, D1~D7 확정, 명세 정본 교정, baseline 정합성 복구,
M0(계약 기준선 고정)까지 완료했다. M1은 시작하지 않았다.

달성한 것: 기존 구현 전체(백엔드 24개 main 파일, 9개 test 파일, 프론트엔드
10개 파일, migration V30~V33)와 v7-2 정본(spec 1799줄 + Schema 2개)의 대응표,
v7-2 정본 내부의 계약 충돌 목록, SDK 버전 분석, 11개 milestone
분할과 각 단계의 검증 명령을 확보했다.

남은 것: **다음 게이트는 M1이 아니다.** `OpenAiResponsesNarrativeAdapterTest`
5건을 신뢰 가능한 상태로 만드는 것이 먼저다(위험 R15). 그 다음 M1 → 검증·리뷰
→ M2 → … 순으로 milestone 하나씩 진행한다.

배운 것 1: v7-2 정본이 단일 문서가 아니라 "Markdown 명세 + JSON Schema 2개 +
그 안의 Java 계약 클래스 예시" 세 겹이고, 세 겹이 서로 어긋났다. 구현을
시작하기 전에 이 세 겹 중 무엇이 이기는지 문서화하지 않으면 drift test 자체를
작성할 수 없다.

배운 것 2: "무관한 미커밋 변경"을 한 덩어리로 취급하면 안 된다. 파일마다
답이 달랐다. 하나는 커밋된 테스트가 요구하는 누락 구현이었고(stash하면 스위트가
깨진다), 다른 셋은 로컬 JDK로는 컴파일조차 안 되는 변경이었다(트리에 두면
아무것도 검증할 수 없다). 격리 전략을 정하기 전에 **각 파일이 테스트에 어떤
영향을 주는지 먼저 측정**해야 한다.

배운 것 3: 전체 스위트가 빨간 상태에서 milestone을 시작할 때는, 깨끗한
worktree에서 기존 실패를 먼저 재현해 baseline을 고정해 두어야 한다. 그래야
"내 변경이 만든 실패"와 "원래 있던 실패"를 증거로 구분할 수 있다.


## Context and Orientation

### 이 저장소에서 "AI 주간 리포트"가 지금 어떻게 동작하는가

용어부터 정의한다.

- **기본 리포트(basic report)**: AI를 쓰지 않는 HTML/PDF 리포트.
  `GET /api/v1/groups/{groupId}/reports/download`가
  `ReportDocumentService.generate()`를 호출해 대시보드 응답을 HTML로 렌더링한다.
  이 경로는 v7-2 범위 밖이며 **변경하지 않는다**.
- **AI 주간 리포트**: `/api/v1/groups/{groupId}/reports/ai-weekly` 아래의
  생성·조회·초안편집·재생성·확정·PDF 다운로드 API 전체.
- **Narrative**: 현재 OpenAI가 생성하는 구조화 출력. headline + summary +
  changes/achievements/risks/topActions/leaderDecisions/limitations 8개 슬롯.
  `ReportContracts.Narrative`에 정의되어 있다.
- **Evidence key / placeholder**: 현재 구현에서 AI가 숫자를 직접 쓰지 못하게
  막는 장치. AI는 `{{tasks.delayed}}` 같은 키만 쓰고 서버가 값을 채운다.
  `NarrativeContract.render()`가 치환한다.
- **Snapshot 동결(frozen snapshot)**: 리포트 생성 시점의 지표·업무·근거를
  DB에 JSON으로 저장해, 이후 업무가 바뀌어도 리포트가 변하지 않게 하는 장치.
  `WeeklyReport.metricsJson / aiContextJson / referenceIndexJson / evidenceJson`.
- **Revision**: 같은 (group, type, period_start, period_end, language)에 대한
  N번째 생성본. unique key로 강제된다.
- **PublicationStatus**: 독자에게 공개되는 상태. `LEGACY`(구 스키마),
  `DRAFT`(팀장만 봄), `FINALIZED`(팀원도 봄, PDF 가능), `SUPERSEDED`(대체됨).

현재 생성 흐름:

    WeeklyReportController.generate (POST)
    → WeeklyReportModule.generateWeeklyAiReport
        (유료 TEAM 그룹 + LEADER 권한 확인, ReportPeriod.completedWeek)
    → WeeklyReportGenerationModule.generate
        → MetricsSnapshotSource.capture  (TaskMetricsSnapshotSource)
        → acquireInitial (짧은 트랜잭션, group row lock, lease 획득, 주 3회 예산)
        → callProvider (트랜잭션 밖)
            → AiNarrativeGenerator.generate  (OpenAiResponsesNarrativeAdapter)
            → NarrativeContract.validateGenerated
            → completeAttempt (attempt 소유권 확인 후 COMPLETED)
    → WeeklyReportGenerationModule.view → WeeklyReportView

현재 실패 정책: OpenAI 호출이나 검증이 실패하면 `markFailed`가 리포트를
`Status.FAILED`로 두고 **예외를 그대로 사용자에게 던진다**. fallback 분석은
존재하지 않는다. v7-2는 여기서 정반대다 — 실패해도 FINALIZED가 되어야 한다.

### v7-2가 요구하는 것

Issue #2가 지정한 정본은 커밋 `bfca41430b32469c29ef513d388fdf096e68e751`의
세 파일이다.

- `docs/spec/AiWeeklyReport.md` — 아키텍처, 4페이지 구조, 검증 계층, SDK 사용법,
  저장 계약, 권한, fallback, 테스트 명세, 구현 순서.
- `docs/contracts/ai-weekly-report-snapshot-v1.schema.json` — OpenAI **입력**
  계약. `$id`는 `https://toesa.local/schemas/ai-weekly-report-snapshot-v1.schema.json`.
  최상위 required: `schemaVersion, reportContext, metrics, comparison, workflow,
  members, tasks, calendarConstraints, riskCandidates`. 전체가
  `additionalProperties: false`.
- `docs/contracts/ai-weekly-report-analysis-v1.schema.json` — OpenAI **출력**
  계약. 최상위 required: `schemaVersion, analysisStatus, executiveJudgment,
  achievement, issues, globalMissingEvidence`. `issues.maxItems = 3`.

v7-2 파이프라인:

    확정 업무 데이터
    → AiWeeklyReportSnapshotV1        (ai-weekly-report-snapshot.v1)
    → 서버 규칙 기반 riskCandidates 생성
    → 공식 openai-java Responses API + Java 타입 Structured Outputs
    → AiWeeklyReportAnalysisContract  (ai-weekly-report-analysis.v1)
    → 서버 의미 검증 (ref 존재 / 허용 코드 부분집합 / 상태 / 비교 / 우선순위 / 날짜)
    → 통과: OPENAI 분석 저장 / 실패: SERVER_FALLBACK 분석 저장
    → 두 경우 모두 FINALIZED
    → v7-2 4페이지 렌더링

### 적용되는 저장소 제약 (`AGENTS.md`)

- 작업과 push는 `member1-work`에서만 한다. `main` 직접 작업 금지.
- Issue는 항상 `--repo study734/WorkTaskFlow`를 명시해 다룬다.
- 무관한 작업 트리 변경을 보존한다. force-push·원격 브랜치 삭제 금지.
- `AGENTS.md`, `.agent/`는 `member1-work` 전용이며 최종 제출에서 제외한다.
- 저장소가 제공하지 않는 lint/test/deploy 명령을 지어내지 않는다.
- 백엔드는 `backend/`에서 Windows 기준 `.\mvnw.cmd test`. 프론트엔드는
  `npm ci` + `npm run build`.

### 알려진 작업 트리 상태

`git status --short` 기준 (이 ExecPlan 작성 시점):

     M .github/workflows/ci.yml                (Java 21 → 25)
     M backend/Dockerfile                      (temurin 21 → 25)
     M backend/pom.xml                         (java.version 21 → 25)
     M backend/src/main/java/com/teamproject/report/application/BasicReportAccessService.java
                                               (무료 그룹 주 2회 제한 제거)
    ?? .serena/                                (추적되지 않는 도구 디렉터리)

이 변경들은 v7-2와 무관하다. 절대 되돌리거나 커밋에 포함하지 않는다.
주의: `backend/pom.xml`은 v7-2 작업에서도 수정해야 하므로(openai-java 버전),
같은 파일에 두 종류의 변경이 섞인다. 커밋 전에 `git add -p`로 v7-2 관련
hunk만 스테이징한다.


## v7-2 정본 내부 충돌표 (Schema vs spec 본문)

**이 표는 2026-07-31 명세 교정 이전의 상태를 기록한 것이다.** `analysisStatus`,
`achievement`, `safeLabel`, 기간, 설정 키, 다운로드 형식은 D1~D7 확정과
`docs/spec/AiWeeklyReport.md` 교정으로 해소되었고, 명세 최상단에 "필드 이름과
enum 값은 JSON Schema가 정본"이라는 우선순위 규칙이 명시되었다.

표를 남겨 두는 이유는 두 가지다. 첫째, 나머지 예시 블록(§4~§6의 JSON)은
설명용으로 그대로 두었으므로 구현 시 어느 쪽을 따라야 하는지 알아야 한다.
둘째, 구현 중 새로운 어긋남을 발견했을 때 무엇이 이미 확인된 차이인지
구분해야 한다. **"권고" 열이 곧 구현이 따라야 할 값이다.**

### 입력 계약 (snapshot)

| 항목 | spec 본문 (§4) | JSON Schema | 권고 |
|---|---|---|---|
| 최상위 | `group`, `period`, `generatedAt`, `language` 분리 | `reportContext{groupRef, period{from,toExclusive,timezone}, generatedAt, language, promptVersion}` | Schema |
| 최상위 `policy` | 존재 | **없음** (`additionalProperties:false`) | Schema — `policy` 전송 금지 |
| 캘린더 | `calendarEvents[]`, 항목 키 `ref`/`ownerRef`/`linkedTaskRefs` | `calendarConstraints[]`, 항목 키 `eventRef`/`relatedTaskRefs`, `ownerRef` 없음 | Schema |
| metrics | `delayedTaskCount`, `statusCounts{...}` | `delayedCount`, `statusCounts` **없음** | Schema — 상태별 개수는 workflow로만 전달 |
| comparison | 중첩 `previousPeriod{}` + `deltas{}` | 평탄화 `previousFrom/previousToExclusive/periodTaskCountDelta/completionRatePointDelta/onTimeRatePointDelta/delayedCountDelta` | Schema |
| workflow | `approvedUnassigned`, `assignedTodo` | `acceptedUnassigned`, `assignedNotStarted` | Schema |
| member | `ref`, `dueSoonCount`, `calendarConflictCount` | `memberRef`, `onTimeRatePercent`, `upcomingCalendarCount` (dueSoonCount 없음) | Schema |
| task | `ref`, `category`, `collaboration.linkedResourceCount`, `history.lastTransition/lastTransitionAt/reopenCount`, `blocker{category,hasRawReason}`, `signals[]` | `taskRef`, `category` 없음, `collaboration.resourceLinkCount`, `history.lastTransitionCode/holdReasonCategory/reopenedCount`, `blocker` 없음, `signalCodes[]` + 항목별 `allowedDecisionOptionCodes`/`allowedExecutionStepCodes`/`allowedCompletionSignalCodes` | Schema |
| 보류 사유 | `blocker.category` | `history.holdReasonCategory` (enum: NONE/EXTERNAL_FEEDBACK/DEPENDENCY/RESOURCE_SHORTAGE/PRIORITY_CHANGE/OTHER/UNKNOWN) | Schema |
| riskCandidate | `code`, `memberRefs`, `eventRefs`, `allowedDecisionOptions`, `allowedExecutionSteps`, `allowedCompletionSignals`, `missingEvidence` | `riskCode`, `memberRefs`/`eventRefs`/`missingEvidence` **없음**, `allowedOptionCodes`/`allowedExecutionStepCodes`/`allowedCompletionSignalCodes` | Schema |
| 위험 코드 §5.2 표 | `APPROVAL_PENDING`, `APPROVED_UNASSIGNED_OVERDUE`, `OVERDUE_ACTIVE`, `ON_HOLD_LONG`, `SCHEDULE_CONFLICT`, `COMPLETION_RATE_DROP`, `BACKLOG_GROWTH` 등 | `riskCode`는 자유 문자열이므로 표 그대로 사용 가능. 단 `evidenceCodes`는 15개 고정 enum(`APPROVED_UNASSIGNED, REQUESTED_PENDING, OVERDUE, DUE_SOON, ON_HOLD, CHECKLIST_NOT_STARTED, CHECKLIST_STALLED, RESOURCE_MISSING, UNRESOLVED_MENTION, WORKLOAD_CONCENTRATION, NO_EFFORT_ESTIMATE, NO_COMPLETION_CRITERIA, CALENDAR_CONFLICT, COMPLETED, ON_TIME_COMPLETED`) | riskCode는 spec 표, evidenceCodes는 Schema enum |
| 배열 상한 | 명시 없음 | `members ≤ 100`, `tasks ≤ 100`, `calendarConstraints ≤ 30`, `riskCandidates ≤ 10` | Schema — assembler가 잘라야 함 |

### 출력 계약 (analysis)

| 항목 | spec 본문 (§6/§10.6) | JSON Schema | 권고 |
|---|---|---|---|
| `analysisStatus` | Java enum `COMPLETE, PARTIAL, NO_ACTION_REQUIRED` | `NORMAL, PARTIAL, NO_ACTION_REQUIRED` | **D1로 escalate** |
| `achievement` | `Optional<Achievement>`, "0~1개" | required object, `status: AVAILABLE\|NONE` + headline/summary/evidenceTaskRefs 필수 | **D2로 escalate** (Optional 사용 시 SDK 생성 스키마가 저장 Schema와 어긋남) |
| `metricRefs` | `"metrics.completionRatePercent"` 문자열 경로 | enum 8종: `PERIOD_TASK_COUNT, COMPLETION_RATE, ON_TIME_RATE, DELAYED_COUNT, PERIOD_TASK_COUNT_DELTA, COMPLETION_RATE_DELTA, ON_TIME_RATE_DELTA, DELAYED_COUNT_DELTA` | Schema |
| `issue.severity` | Java 필드 타입 `String` | enum `HIGH\|MEDIUM\|LOW` | Schema (Java enum으로 선언) |
| `recommendedOptionCode` 예시 | `ASSIGN_OWNER` | enum: `ASSIGN_OWNER_AND_SET_DUE, DEFINE_HOLD_EXIT_CRITERIA, DEFER_SCOPE, APPROVE_SCOPE, REQUEST_MORE_EVIDENCE, REBALANCE_WORK, KEEP_CURRENT_PLAN` | Schema |
| `executionStepCodes` 예시 | `ASSIGN_TASK_OWNER, RESET_TASK_DUE, START_FIRST_CHECKLIST_ITEM` | enum: `ASSIGN_OWNER, SET_DUE, START_CHECKLIST, LINK_RESOURCE, RESOLVE_MENTION, SET_HOLD_EXIT_CRITERIA, RESUME_TASK, RECORD_SCOPE_DECISION, SET_NEXT_REVIEW_DATE, REBALANCE_ASSIGNEE` | Schema |
| `completionSignalCodes` 예시 | `ASSIGNEE_PRESENT, DUE_DATE_PRESENT, CHECKLIST_PROGRESS_GT_ZERO` | enum: `ASSIGNEE_SET, DUE_AT_SET, CHECKLIST_STARTED, RESOURCE_LINKED, MENTION_RESOLVED, HOLD_STATE_RECORDED, TASK_RESUMED, SCOPE_DECISION_RECORDED, NEXT_REVIEW_DATE_SET` | Schema |
| `decisionMakerRole` | `"LEADER"` (자유 문자열) | enum `LEADER\|GROUP_ADMIN` | Schema |
| `actionOwnerRole` 예시 | `ASSIGNEE_TO_BE_SELECTED` | enum `SELECTED_MEMBER\|CURRENT_ASSIGNEE\|REQUESTER\|LEADER\|TEAM` | Schema |
| `deadline.source` | `MEETING_END, TASK_DUE, CALENDAR_EVENT, LEADER_DECISION_REQUIRED` | 동일 | 일치 ✓ |
| `deadline.referenceRef` | `Optional<String>` | `["string","null"]`, required | Schema (nullable, 항상 존재) |
| 문자열 길이 | 명시 없음 | headline ≤160, interpretation ≤360, issue.title ≤80, impact ≤220, integratedJudgment ≤320, requiredDecision ≤120, decision.recommendation ≤260 등 | Schema |
| `issues` 상한 | 최대 3개 (§6.3) | `maxItems: 3` | 일치 ✓ |
| priority 연속성 | P1부터 연속 (§7.5) | Schema에 없음 (enum만) | Validator가 검사 |


## 대응표 1 — 기존 파일별 유지 / 수정 / 삭제

### 백엔드 main

| 파일 (`backend/src/main/java/com/teamproject/report/`) | 조치 | 근거와 후속 |
|---|---|---|
| `application/AiNarrativeGenerator.java` | **삭제** | v7-2 §10.5의 `application/port/AiWeeklyReportGateway.java`로 대체 |
| `application/NarrativeContract.java` (1097줄) | **삭제** | v7-2에서 프롬프트는 리소스 파일, 스키마는 SDK 생성, 검증은 `AiWeeklyReportAnalysisValidator`, 렌더 값 치환은 renderer가 담당. placeholder/evidence-key 계약 자체가 폐기된다 |
| `application/ReportContracts.java` (434줄) | **삭제** | `Narrative*`, `AiReportContext`, `MetricsSnapshot`, `OperationalView`, `WeeklyReportView`, `AiGenerationInput/Result` 전부 v7-2 대응물 없음. 신규 `application/dto/AiWeeklyReportDtos.java`로 교체 |
| `application/MetricsSnapshotSource.java` | **삭제** | `ReportSnapshot` 계약 전용 인터페이스. `AiWeeklyReportSnapshotAssembler`로 대체 |
| `application/MemberPerformanceRule.java` (130줄) | **삭제** | v7-2 Non-goal "팀원 등급과 순위" |
| `application/ReportPeriod.java` | **수정 또는 삭제** | D3에 종속. 7일 고정이면 v7-2 전용 period 값 객체로 교체. 사용처는 AI 리포트 경로뿐이라 기본 리포트에 영향 없음 |
| `application/WeeklyReportGenerationModule.java` (474줄) | **삭제 후 재작성** | lease/attempt/2축 lifecycle/초안편집/supersede가 v7-2 상태 모델과 맞지 않음. `AiWeeklyReportService`(v7-2 §14 오케스트레이션)로 교체 |
| `application/WeeklyReportModule.java` | **수정** | 권한 검사(`requirePaidTeamLeader`/`requirePaidTeamMember`) 로직은 **재사용**, 초안편집·재생성·확정 메서드는 D4 결과에 따라 제거 |
| `application/WeeklyReportPdfService.java` | **수정** | 저장 revision을 읽어 renderer에 넘기는 구조는 유지. `WeeklyReportView` → v7-2 view 타입으로 교체. `FINALIZED만 다운로드` 규칙은 v7-2 §9.3과 일치하므로 유지 |
| `application/ReportPdfRenderer.java` | **수정** | `renderBasic` 유지, `renderWeeklyAi(WeeklyReportView)` 시그니처를 v7-2 view로 교체 |
| `application/GeneratedPdf.java` | **유지** | 파일명 + 바이트 record. 그대로 사용 |
| `application/BasicReportAccessService.java` | **유지** | 기본 리포트 경로. 미커밋 변경 보존 |
| `application/BasicReportPdfService.java` | **유지** | 기본 리포트 경로 |
| `application/ReportDocumentService.java` | **유지** | `/reports/download` 기본 HTML. v7-2 §20 제약 "기존 /reports/download 응답과 기본 HTML 스타일을 변경하지 않는다" |
| `application/ReportScheduleService.java` | **유지** | 이메일 스케줄. AI 리포트와 무관 |
| `application/dto/ReportDtos.java` | **유지** | 스케줄/기본 리포트 DTO |
| `domain/WeeklyReport.java` (312줄) | **유지 (읽기 전용 legacy)** | D5 권고안 기준. 기존 행 조회·인쇄만 남기고 신규 생성 경로에서 분리. 신규 `domain/AiWeeklyReportRevision.java` 추가 |
| `domain/WeeklyReportRepository.java` | **수정** | legacy 조회 메서드만 남기고 lease/budget/lock 쿼리 제거 |
| `domain/ReportSchedule*.java`, `domain/ReportDelivery*.java` | **유지** | 스케줄 도메인 |
| `infrastructure/OpenAiReportConfiguration.java` | **수정** | Bean 이름 `openAiReportClient` 유지. `maxRetries(0)` → `1`, `responseValidation(true)` 추가, timeout `90s` → `45s`. **설정 prefix `app.ai-report.*`는 그대로 유지한다 (D6)**. `fromEnv()` 대신 명시적 `.apiKey(...)` 유지 |
| `infrastructure/OpenAiResponsesNarrativeAdapter.java` (165줄) | **삭제 후 재작성** | 예외 분류 로직(`causedByTimeout`, `OpenAIIoException`/`OpenAIInvalidDataException`/`OpenAIException` 분기)과 `output()→message()→content()→outputText()` 탐색·refusal 처리는 **패턴을 그대로 재사용**. 출력 타입만 `GeneratedNarrative` → `AiWeeklyReportAnalysisContract`로 교체하고, 예외를 사용자 예외 대신 fallback 트리거로 바꾼다 |
| `infrastructure/OpenHtmlReportPdfRenderer.java` (350줄) | **수정** | `renderBasic`(38~78행)은 **그대로 유지**. `renderWeeklyAi`(80~181행)의 섹션 구성 전체를 v7-2 4페이지로 교체. HTML escape(`html()`), 폰트 로딩, `@page A4` CSS, `page-break-inside:avoid`는 재사용 |
| `infrastructure/TaskMetricsSnapshotSource.java` (833줄) | **삭제 후 재작성** | evidence key 체계·등급 계산이 v7-2와 무관. 단 bulk 조회 구조, `ActivitySnapshot`, `dueState` 계산, 이전 기간 비교는 `AiWeeklyReportSnapshotAssembler` + `TaskEvidenceQueryRepository`로 **이식**한다 |
| `infrastructure/TaskFlowMetrics.java` | **수정** | 흐름 집계 유틸. v7-2 `workflow` 6개 필드(requested/acceptedUnassigned/assignedNotStarted/inProgress/onHold/completed) 산출로 재사용 |
| `presentation/WeeklyReportController.java` (131줄) | **삭제 후 재작성** | 엔드포인트 계약이 통째로 바뀐다 (아래 대응표 참조) |
| `presentation/ReportController.java` | **유지** | 스케줄 + 기본 다운로드 |
| `presentation/BasicReportPdfController.java` | **유지** | 기본 리포트 PDF |

### 백엔드 신규 (v7-2 §13)

    presentation/AiWeeklyReportController.java
    application/AiWeeklyReportService.java
    application/AiWeeklyReportSnapshotAssembler.java
    application/AiWeeklyReportPolicyEngine.java
    application/AiWeeklyReportAnalysisValidator.java
    application/AiWeeklyReportFallbackFactory.java      (§16)
    application/AiWeeklyReportViewProjector.java
    application/AiWeeklyReportDocumentService.java
    application/port/AiWeeklyReportGateway.java
    application/dto/AiWeeklyReportDtos.java
    domain/AiWeeklyReportRevision.java
    domain/AiWeeklyReportStatus.java
    domain/AiAnalysisMode.java
    infrastructure/AiWeeklyReportRevisionRepository.java
    infrastructure/TaskEvidenceQueryRepository.java
    infrastructure/openai/OpenAIConfiguration.java
    infrastructure/openai/OpenAiReportProperties.java
    infrastructure/openai/OpenAiReportPrompt.java
    infrastructure/openai/OpenAiWeeklyReportGateway.java
    infrastructure/openai/OpenAiAnalysisContractMapper.java
    infrastructure/openai/OpenAiReportException.java
    infrastructure/openai/contract/AiWeeklyReportAnalysisContract.java

    backend/src/main/resources/db/migration/V34__create_ai_weekly_report_revision.sql
    backend/src/main/resources/ai/v7-2-prompt-001.txt
    backend/src/main/resources/ai/ai-weekly-report-analysis-v1.schema.json
    backend/src/main/resources/ai/ai-weekly-report-snapshot-v1.schema.json

`application`과 `domain` 패키지는 `com.openai.*`를 import하지 않는다 (v7-2 §13).

### API 엔드포인트 대응

| 현재 | v7-2 | 조치 |
|---|---|---|
| `POST /groups/{g}/reports/ai-weekly` body `{weekStart, language}` → 200/201 | `POST` body `{from, toExclusive, language, regenerate}` → 201/200/202 | **교체** |
| `GET /groups/{g}/reports/ai-weekly?weekStart&language` | v7-2에 대응 없음 | **제거** (프론트가 reportId로 조회하도록 변경) |
| `GET /groups/{g}/reports/ai-weekly/{id}` | `GET .../{reportId}` | **유지 (응답 본문 교체)** |
| `GET /groups/{g}/reports/ai-weekly/{id}/pdf` | 동일 경로 유지 | **유지 (D7)** — 실제 PDF, `application/pdf`. 본문만 v7-2 4페이지로 교체 |
| `GET /groups/{g}/reports/ai-weekly/revisions?weekStart&language` | v7-2에 대응 없음 | **D4** — revision 목록 UI 유지 여부 |
| `PATCH /groups/{g}/reports/ai-weekly/{id}/draft` | 없음 | **D4** — 제거 후보 |
| `POST /groups/{g}/reports/ai-weekly/{id}/regenerations` | `POST` 본문 `regenerate:true`로 흡수 | **제거** |
| `POST /groups/{g}/reports/ai-weekly/{id}/finalization` | 없음 (생성 즉시 FINALIZED) | **D4** — 제거 후보 |

### 프론트엔드

| 파일 | 조치 | 근거 |
|---|---|---|
| `frontend/src/api/reportApi.ts` (355줄) | **수정** | `LocalReference`, `EvidenceValue`, `Narrative*View`, `NarrativeDraft*`, `MemberWorkView`, `TaskWorkView`, `OperationalView`, `WeeklyAiReport.metrics/comparison/evidence/analysis/draft/gradeRule` 타입 전부 교체. `requireWeeklyReport` fail-closed 검증 패턴은 **유지**하고 검사 대상 필드만 v7-2로 교체. `schedule/updateSchedule/download`(기본 리포트)는 **그대로 유지** |
| `frontend/src/features/report/reportProjection.ts` (354줄) | **삭제** | `SERVER_RISK_RULES`(OVERDUE_PRESENT/ON_HOLD_PRESENT/HIGH_PRIORITY_PRESENT)와 evidenceKey 기반 업무 매칭은 v7-2에서 서버가 `riskCandidates.taskRefs`로 확정한다. scope/density URL 상태 관리(`readReportProjectionState` 등)는 **D4에 따라** 별도 파일로 축소 이전 가능 |
| `frontend/src/features/report/components/AiReportContent.tsx` (844줄) | **삭제 후 재작성** | 현재 12개 섹션 단일 스크롤 구조를 v7-2 4페이지로 교체. `MemberPerformance`(등급표)와 `MemberExceptions`는 삭제, "정상 팀원 전체표" 제거 요구와 직결 |
| `frontend/src/features/report/components/AiReportDraftEditor.tsx` (162줄) | **삭제 (D4)** | 초안 편집 제거 시 |
| `frontend/src/features/report/components/AiWeeklyReportPanel.tsx` (401줄) | **수정** | 생성 트리거·상태 표시. 주차 선택 UI가 D3(7일 고정)에 종속 |
| `frontend/src/features/report/components/AiWeeklyReportAction.tsx` (151줄) | **수정** | 대시보드 진입점 |
| `frontend/src/features/report/components/aiReportWindow.ts` (293줄) | **수정** | 인쇄 창 제어 |
| `frontend/src/features/report/components/reportPrintRenderer.ts` (3줄) | **유지** | 얇은 재노출 모듈 |
| `frontend/src/features/report/pages/AiWeeklyReportDetailPage.tsx` (129줄) | **수정** | 상세 페이지 |
| `frontend/src/features/report/pages/AiWeeklyReportPrintPage.tsx` (49줄) | **수정** | 인쇄 페이지 |
| `frontend/src/features/dashboard/pages/GroupDashboardPage.tsx` | **수정** | AI 리포트 진입 지점만 |
| `frontend/src/app/App.tsx` | **수정** | 라우트 파라미터가 바뀌는 경우만 |

### 테스트

| 파일 | 조치 |
|---|---|
| `backend/src/test/java/com/teamproject/report/NarrativeContractTest.java` (589줄, 25 테스트) | **삭제** — 검증 대상 클래스가 사라짐. 단 "허용되지 않은 참조 거부", "완료 업무를 지연 근거로 인용 거부", "숫자 리터럴 거부" 세 가지 **의도**는 새 `AiWeeklyReportAnalysisValidatorTest`로 이식 |
| `backend/src/test/java/com/teamproject/report/OpenAiResponsesNarrativeAdapterTest.java` (254줄) | **삭제 후 재작성** — `com.sun.net.httpserver` 기반 로컬 스텁 서버 인프라는 재사용, 검증 대상만 새 gateway로 교체 |
| `backend/src/test/java/com/teamproject/report/WeeklyReportModuleTest.java` (498줄, 9 테스트) | **삭제 후 재작성** — lease/attempt/주3회 예산 테스트는 v7-2 상태 모델에 대응물 없음 |
| `backend/src/test/java/com/teamproject/report/WeeklyReportLifecycleTest.java` (315줄, 5 테스트) | **삭제 후 재작성** — 초안편집·확정·supersede는 D4 결과에 종속 |
| `backend/src/test/java/com/teamproject/report/WeeklyReportApiTest.java` (295줄, 3 테스트) | **수정** — `freeGroupIsLockedAndPaidLeaderCreatesThenReadsCachedReport`(유료 게이팅)와 `basicReportDownloadsAsPdfWithoutOpenAi`(기본 리포트 회귀)는 **유지 가치가 높다**. AI 엔드포인트 부분만 교체 |
| `backend/src/test/java/com/teamproject/report/MemberPerformanceRuleTest.java` (143줄, 11 테스트) | **삭제** — 대상 클래스 삭제 |
| `backend/src/test/java/com/teamproject/report/TaskActivityMetricsSnapshotSourceTest.java` (287줄, 7 테스트) | **삭제 후 재작성** — "완료된 주의 스냅샷은 이후 변경에 흔들리지 않는다", "활동 이력 없는 legacy 업무는 partial", "보류 enum 변환", "보류 dwell 측정" 네 가지 의도는 새 assembler 테스트로 이식 |
| `backend/src/test/java/com/teamproject/report/ReportDocumentServiceTest.java` (49줄) | **유지** — 기본 리포트 |
| `backend/src/test/java/com/teamproject/report/manual/ManualAiReportApplication.java` | **수정** — `ReportContracts`/`ReportPeriod` 참조 |
| `backend/src/test/java/com/teamproject/migration/MySqlFlywayMigrationTest.java` | **수정** — head `33` → `34`, 메서드명 `...ThroughV33` → `...ThroughV34` |
| `frontend/e2e/ai-weekly-report.chrome.spec.ts` (1508줄, 15 테스트) | **대부분 삭제 후 재작성** — 아래 세부 참조 |

E2E 15건 세부 조치:

- 유지 가치가 높아 **이식**: `기본 리포트와 AI 리포트가 범위·기간 선택을
  공유한다`, `기본 리포트도 팝업 없이 PDF 파일로 다운로드한다`,
  `호환되지 않는 캐시 응답은 대시보드를 백지화하지 않고 오류 상태를 표시한다`.
- **삭제**(계약 소멸): `표준 리포트 reader에서 편집·재생성·확정한다`(D4),
  `finalized print 화면은 frozen 위험 순서와 기존 근거 마크업을 유지한다`,
  `첫 리포트의 BASELINE과 서버 위험·AI 위험을 구분하고 밀도를 전환한다`,
  `명시적 taskRefs와 서버 fallback을 frozen 업무에만 연결한다`,
  `팀원 예외를 위험·업무·지연·ref 순서로 최대 3명 선택하고 빈 상태를 표시한다`,
  `MEMBER_COMPARISON은 frozen 순서와 서버 위험 업무만 투영한다`,
  `INDIVIDUAL_MEMBER는 frozen KPI와 선택 팀원 업무만 투영한다`,
  `invalid projection query를 기본값으로 복구하고 comparison memberRef를
  제거한다`, `frozen member 선택을 유지하고 projection 전환 중 report API를
  다시 호출하지 않는다`, `stale GENERATING 리포트를 Chrome에서 다시 획득한다`,
  `지난주 대비 변화를 부호가 아니라 방향으로 읽는다`,
  `같은 안내 문장을 위험 항목마다 반복하지 않는다`.
- **신규**: v7-2 4페이지 구조 확인, AI 핵심 판단 정확히 1개, 이슈 최대 3개,
  기술 용어 미노출, 모든 AI 카드의 근거 ref 표시, fallback 모드 하단 문구.


## 대응표 2 — 기존 revision migration 정책

이 절은 Issue #2 Done criteria "legacy revision 처리 정책이 테스트로 검증된다"에
직접 대응한다.

### 현재 저장 상태

`reports` 테이블(V30 생성, V33 확장)에 `type='WEEKLY_AI'` 행이 쌓여 있다.
`schema_version` 컬럼 값은 `v2`, `v3`, `v4` 세 가지다
(`NarrativeContract.SCHEMA_VERSION = "v4"`, `readNarrative`가 v2/v3/v4를 분기).
`publication_status`는 `LEGACY`(v2 행) 또는 `DRAFT`/`FINALIZED`/`SUPERSEDED`
(v3/v4 행)다. `period_end`는 **포함 날짜**다.

### 확정 정책 (D5)

1. **`reports` 테이블은 스키마를 변경하지 않는다.** 데이터 변환도 하지 않는다.
   기존 행은 그대로 남는다.
2. 새 테이블 `ai_weekly_report_revision`을 `V34__create_ai_weekly_report_revision.sql`
   로 추가한다. v7-2 §12.1의 컬럼 목록을 그대로 쓰되 MySQL/InnoDB 관례에 맞춘다
   (`JSON` 컬럼, `CHAR(64)` source_hash, `DATETIME(6)`).
   추가로 다음 제약을 둔다:
   - `UNIQUE (group_id, period_from, period_to_exclusive, language, revision)`
   - `INDEX (group_id, period_from, period_to_exclusive, language, status, source_hash)`
     — v7-2 §12.3 source hash 재사용 조회용
   - `FOREIGN KEY (group_id) REFERENCES work_groups(id)`,
     `FOREIGN KEY (generated_by_user_id) REFERENCES users(id)`
3. **legacy 행 노출 정책**: `GET /groups/{g}/reports/ai-weekly/{reportId}`는
   신규 테이블만 조회한다. 구 `reports` 행의 id로 접근하면
   `410 Gone` + 코드 `AI_REPORT_LEGACY_REVISION`을 반환하고,
   "이전 형식으로 생성된 리포트입니다. 새로 생성해 주세요."를 안내한다.
   Issue #2의 "기존 계약과 새 계약이 동시에 활성 상태로 남아 있지 않다"를
   만족시키려면 legacy 행을 **읽기조차 v7-2 화면으로 렌더링하지 않아야** 한다.
4. **legacy 생성 경로 차단**: `WeeklyReportGenerationModule`이 삭제되면
   `reports`에 새 `WEEKLY_AI` 행이 생길 수 없다. `WeeklyReport` 엔티티는
   `ReportSchedule`/`ReportDelivery`와 무관하므로 JPA 매핑을 남겨도
   부작용이 없다. 남기는 이유는 테이블 drop 없이 데이터를 보존하기 위함이다.
5. **되돌리기**: V34는 `CREATE TABLE`만 하므로 롤백은 `DROP TABLE
   ai_weekly_report_revision` + Flyway history 행 삭제로 끝난다. 기존 데이터
   손실이 없다.

### 검증 (M5에서 작성)

- `MySqlFlywayMigrationTest`: head가 `34`이고 V1→V34가 빈 MySQL에서 성공.
- `AiWeeklyReportLegacyPolicyTest` (신규, `@SpringBootTest` + Testcontainers):
  - `reports`에 `type='WEEKLY_AI'`, `schema_version='v4'`, `publication_status='FINALIZED'`
    행을 직접 INSERT한다.
  - 그 id로 `GET .../ai-weekly/{id}` 호출 → `410` + `AI_REPORT_LEGACY_REVISION`.
  - 같은 그룹·기간으로 `POST .../ai-weekly` 호출 → `201`, 새 테이블에 revision 1
    생성, 기존 `reports` 행은 **변경되지 않음**(updated_at 동일).
  - 새 테이블 revision 1을 만든 뒤 `regenerate:true` → revision 2 생성,
    revision 1 행은 삭제되지 않음 (v7-2 §12.4).

### 만약 D5가 "in-place 변환"으로 결정된다면

권고하지 않지만 결정될 경우의 필수 조건을 기록해 둔다: (a) `period_end`
→ `period_to_exclusive` 변환은 `period_end + INTERVAL 1 DAY`이며 달 기준
절단 주차(1~3일)는 v7-2의 7일 규칙을 위반하므로 변환 대상에서 제외해야 한다.
(b) `metrics_json`/`ai_summary_json`은 v7-2 Snapshot/Analysis로 무손실 변환이
불가능하다(evidence key 체계가 없음). (c) 따라서 in-place 변환은 실질적으로
"legacy 행 삭제"와 같으며, 되돌릴 수 없다.


## 대응표 3 — 권한과 publication lifecycle 유지 여부

### 권한

| 기능 | 현재 구현 | v7-2 §15 / §21.2 | 권고 |
|---|---|---|---|
| 그룹 종류 | TEAM만 (`PERSONAL_GROUP_RESTRICTED`) | 언급 없음 | **유지** |
| 요금제 | PAID만 (`AI_REPORT_PAID_REQUIRED`) | 언급 없음 | **유지** — 제거하면 과금 정책이 무너진다 |
| 생성·재생성 | LEADER | LEADER | **유지** |
| 조회 (id) | 활성 MEMBER. 단 LEADER가 아니면 `FINALIZED`만 (`AI_REPORT_NOT_FINALIZED` 403) | 활성 MEMBER 조회 가능 | **단순화** — v7-2는 생성 즉시 FINALIZED이므로 DRAFT 가시성 분기가 불필요해진다 |
| 조회 (주차별) | LEADER만 | 대응 없음 | 엔드포인트 제거 |
| 다운로드 | 활성 MEMBER + `FINALIZED`만 | 활성 MEMBER | **유지** |
| Snapshot 원본 조회 | 노출 안 함 | 전원 금지 | **유지** — snapshot_json을 어떤 API로도 반환하지 않는다 |
| 분석 로그 조회 | 없음 | 관리자만 | 범위 밖 (구현하지 않음) |
| 생성 횟수 | 같은 주 성공 3회 (`AI_REPORT_WEEKLY_LIMIT` 429) | 언급 없음 | **유지 권고** — 비용 통제 장치. 제거하면 무한 재생성 가능 |

`WeeklyReportModule.requirePaidTeamMember` / `requirePaidTeamLeader`
(112줄 파일의 84~106행)는 **그대로 재사용**한다. v7-2 §14가 요구하는
`authorization.requireAiReportGeneration(userId, groupId)`은 이 두 메서드로
충족된다.

### Publication lifecycle

| 축 | 현재 | v7-2 §12.2 | 권고 |
|---|---|---|---|
| 생성 상태 | `PENDING → GENERATING → COMPLETED \| FAILED` | `GENERATING → FINALIZED \| FAILED` | **교체** |
| 공개 상태 | `LEGACY / DRAFT / FINALIZED / SUPERSEDED` 별도 축 | 없음 (단일 축) | **제거** |
| 낙관적 잠금 | `editor_version` + `expectedEditorVersion` | 없음 | **제거** |
| 초안 편집 | `PATCH /{id}/draft` + `NarrativeContract.validateDraft` | 없음 | **D4** |
| 확정 | `POST /{id}/finalization` + 이전 확정본 supersede | 없음 (생성 즉시 FINALIZED) | **D4** |
| 실패 | `FAILED` + 사용자에게 예외 전파 | `FAILED`는 서버 내부 실패에만. OpenAI 실패는 `FINALIZED` + `analysis_mode=SERVER_FALLBACK` | **교체** |
| 중복 방지 | 같은 (group, period, language)의 최신 revision 재사용 + 2분 lease | `source_hash` 재사용 (§12.3), `regenerate=false`면 기존 FINALIZED 재사용 | **교체** |
| 동시 생성 차단 | group row `SELECT ... FOR UPDATE` + lease 만료 | DB unique key 또는 application lock (§14) | **유지 (구조 이식)** — group row lock 패턴은 검증된 자산이므로 새 서비스에 그대로 옮긴다 |
| 트랜잭션 경계 | 짧은 DB 트랜잭션 / 외부 호출 분리 | 동일 요구 (§14) | **유지 (구조 이식)** |

즉 **상태 모델은 교체하되 동시성 제어 구조는 이식한다**. `hasExpiredLease` /
`ownsGeneratingAttempt` 개념은 v7-2의 `GENERATING` 상태에도 필요하다
(생성 도중 프로세스가 죽으면 영구히 GENERATING으로 남기 때문). v7-2 spec은
이 문제를 다루지 않으므로, 현재 구현의 lease 패턴을 **유지 근거를 남기고**
새 서비스로 옮긴다.


## 대응표 4 — v7-2 Snapshot / Analysis 계약 적용 방법

### Snapshot 필드별 데이터 출처

`AiWeeklyReportSnapshotAssembler`가 채운다. "출처"는 현재 저장소에서 그 값을
가져올 수 있는 위치다.

| Snapshot 경로 | 출처 | 비고 |
|---|---|---|
| `schemaVersion` | 상수 `"ai-weekly-report-snapshot.v1"` | |
| `reportContext.groupRef` | `"GROUP-" + groupId` | 익명 ref |
| `reportContext.period.from/toExclusive` | 요청 파라미터 | 7일 검증 (D3) |
| `reportContext.period.timezone` | `Group.getTimezone()` | |
| `reportContext.generatedAt` | `Clock.instant()` | ISO-8601 |
| `reportContext.language` | 요청 (`KO`/`EN`) | 현재 API는 소문자 `ko`/`en` → 대문자 변환 필요 |
| `reportContext.promptVersion` | `OpenAiReportProperties.promptVersion` (`v7-2-prompt-001`) | |
| `metrics.periodTaskCount` | `TaskMetricsSnapshotSource.calculate` 이식 | |
| `metrics.completionRatePercent` | 동일 | nullable |
| `metrics.onTimeRatePercent` | 동일 | nullable |
| `metrics.delayedCount` | `StatusMetrics.delayed` | |
| `metrics.averageCompletionHours` | 동일 | nullable, integer로 반올림 |
| `comparison.*` | `TaskMetricsSnapshotSource.compare` 이식 | 이전 7일 기간. baseline 없으면 `status:"NO_BASELINE"` + 나머지 전부 null |
| `workflow.requested/inProgress/onHold/completed` | 상태별 집계 | |
| `workflow.acceptedUnassigned` | `status=TODO AND assignee IS NULL` | |
| `workflow.assignedNotStarted` | `status=TODO AND assignee IS NOT NULL` | |
| `members[].memberRef` | `"MEMBER-" + index` (그룹 내 결정적 별칭) | 실명 금지 |
| `members[].role` | `GroupMember.Role` | |
| `members[].assigned/active/completed/delayed` | `memberMetrics` 이식 | |
| `members[].onTimeRatePercent` | 동일, nullable | |
| `members[].upcomingCalendarCount` | 캘린더 bulk 조회 | 신규 |
| `tasks[].taskRef` | `"TASK-" + index` | |
| `tasks[].safeLabel` | **업무 제목 + 개인정보 필터, 120자 절단** | **신규 외부 전송 — D2 참조** |
| `tasks[].status` | `Task.status` | enum 7종 일치 |
| `tasks[].priority` | `Task.priority`, nullable | |
| `tasks[].assigneeRef` | memberRef 또는 null | |
| `tasks[].createdAt/dueAt/completedAt` | `task_activity_events` 동결 값 | |
| `tasks[].dueState` | `TaskMetricsSnapshotSource.dueState` 이식. v7-2 enum 6종(`NO_DUE/UPCOMING/DUE_SOON/OVERDUE/COMPLETED_ON_TIME/COMPLETED_LATE`)으로 매핑 | 현재 `NONE` → `NO_DUE` |
| `tasks[].checklist.{completed,total}` | `task_checklist_items` bulk 집계 | |
| `tasks[].collaboration.commentCount` | `task_comments` bulk 집계 | 신규 |
| `tasks[].collaboration.unresolvedMentionCount` | `comment_mentions` bulk 집계 | 신규 |
| `tasks[].collaboration.resourceLinkCount` | 자료 연결 bulk 집계 | 신규 |
| `tasks[].history.lastTransitionCode` | `task_activity_events.event_type` 최신 | nullable |
| `tasks[].history.holdReasonCategory` | `tasks.blocker_type`(V32) 매핑. 매핑 불가면 `UNKNOWN` | v7-2 §21.1 권고와 일치 |
| `tasks[].history.reopenedCount` | 활동 이벤트에서 `REOPENED` 개수 | |
| `tasks[].calendarEventRefs` | 캘린더 연결 | |
| `tasks[].signalCodes` | policy engine이 산출 (15종 enum) | |
| `tasks[].allowed*Codes` | policy engine이 산출 | |
| `calendarConstraints[]` | `calendar_events`(V11) bulk 조회, `safeLabel` 160자 | **신규 외부 전송** |
| `riskCandidates[]` | `AiWeeklyReportPolicyEngine` | ≤ 10개 |

**개인정보 경계 (v7-2 §4.8)** — 다음은 Snapshot JSON에 절대 넣지 않는다:
실명, 댓글 원문, 업무 description 원문, 첨부파일 본문, 자유 입력 보류 사유
원문, 이메일, 전화번호, 사용자 ID. 이를 강제하는 테스트를 M2에 둔다
(직렬화 결과 문자열에 fixture의 실명·댓글 문자열이 없음을 단언).

### Analysis 계약 적용

1. **Java 계약 클래스** `infrastructure/openai/contract/AiWeeklyReportAnalysisContract.java`
   를 v7-2 §10.6 형태로 작성하되, 필드 이름·enum 값은 저장 JSON Schema를 따른다
   (위 충돌표). `Map` 금지, 모든 schema 대상 클래스는 최소 1개 public 필드.
2. **SDK가 이 클래스에서 JSON Schema를 생성**한다
   (`ResponseCreateParams.builder().text(AiWeeklyReportAnalysisContract.class)`).
   Context7 확인 결과 이 호출은 local JSON schema validation을 수행하고,
   실패 시 예외를 던진다. 우리는 이 검증을 **끄지 않는다**
   (`JsonSchemaLocalValidation.NO` 사용 금지 — v7-2 §17 SDK smoke test).
3. **Drift test**: `AiWeeklyReportAnalysisContractDriftTest`가
   `backend/src/main/resources/ai/ai-weekly-report-analysis-v1.schema.json`을
   읽어 저장 Schema의 `properties` 키 집합·`required` 집합·모든 `enum` 값
   집합이 Java 계약 클래스의 필드·enum과 일치함을 단언한다. 배열 최대 길이와
   cross-field 규칙은 이 테스트가 아니라 Validator가 담당한다 (§6 런타임 정본
   문단).
4. **의미 검증** `AiWeeklyReportAnalysisValidator`가 v7-2 §7 전체를 구현한다:
   - §7.1 ref 존재: candidateRef ∈ snapshot.riskCandidates,
     taskRef ∈ snapshot.tasks, eventRef ∈ snapshot.calendarConstraints,
     metricRef ∈ 8종 enum이면서 baseline 없으면 `*_DELTA` 금지
   - §7.2 부분집합: `recommendedOptionCode` ∈ candidate.allowedOptionCodes,
     `executionStepCodes` ⊆ candidate.allowedExecutionStepCodes,
     `completionSignalCodes` ⊆ candidate.allowedCompletionSignalCodes
   - §7.3 상태: achievement taskRef는 `COMPLETED`, 미할당 위험은
     `assigneeRef == null`, 지연 위험은 `dueState == OVERDUE`, 보류 위험은
     `status == ON_HOLD`, 체크리스트 위험은 snapshot checklist 값과 일치
   - §7.4 비교: `comparison.status == NO_BASELINE`이면 delta metricRef 금지
   - §7.5 우선순위: `[]`, `[P1]`, `[P1,P2]`, `[P1,P2,P3]`만 허용
   - §7.6 날짜: `deadline.source`가 4종 enum이고, `LEADER_DECISION_REQUIRED`가
     아니면 `referenceRef`가 snapshot에 존재
   - §6.4 confidence: `INSUFFICIENT_EVIDENCE`면 `missingEvidence ≥ 1`,
     `HIGH`인데 `missingEvidence`가 있으면 실패
5. **렌더링 재결합** (§8): AI 문자열에는 수치·날짜·실명이 없다. renderer가
   `metricRefs`/`taskRefs`/`eventRefs`/`memberRefs`를 서버 데이터로 되돌려
   실제 label·숫자·날짜·이름을 삽입한다.


## OpenAI SDK 버전 분석 (4.45.0 기준, 승격 보류)

### 현재 확인된 사실

    $ cd backend && ./mvnw --batch-mode -o dependency:tree -Dincludes=com.openai
    [INFO] \- com.openai:openai-java:jar:4.45.0:compile
    [INFO]    \- com.openai:openai-java-client-okhttp:jar:4.45.0:compile
    [INFO]       \- com.openai:openai-java-core:jar:4.45.0:compile

    $ ./mvnw --batch-mode -o dependency:tree | grep -E 'openai|jackson'
    ... com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.17.2
    ... com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2
    ... com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.17.2
    ... com.openai:openai-java:4.45.0
    ...   com.openai:openai-java-core:4.45.0
    ...     com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2 (runtime)
    ...     com.github.victools:jsonschema-module-jackson:4.38.0 (runtime)
    ... com.fasterxml.jackson.core:jackson-databind:2.17.2

### 결론

0. **승격은 보류되었다 (2026-07-31).** `4.47.0`이 Maven Central에 없어 채택할
   수 없다. 아래 1~4항은 Central에 배포된 뒤 승격을 재검토할 때를 위한 분석으로
   남긴다. 현재 v7-2 빌드 기준은 `4.45.0`이다.

1. **승격 자체는 저위험으로 보인다.** `4.45.0 → 4.47.0`은 같은 major·minor 계열의
   patch 승격이며, 현재 코드가 이미 쓰는 API 표면
   (`OpenAIOkHttpClient.builder()`, `ResponseCreateParams.builder().text(Class)`,
   `StructuredResponseCreateParams<T>`, `StructuredResponse<T>`,
   `StructuredResponseOutputMessage.Content<T>`, `ResponseStatus.COMPLETED`,
   `ResponseUsage`, `ResponsesModel`, `OpenAIException`/`OpenAIIoException`/
   `OpenAIInvalidDataException`)이 Context7가 확인한 4.4x 문서와 일치한다.
   즉 v7-2가 요구하는 Responses API + Java 타입 Structured Outputs는
   **이미 이 저장소에서 동작 중인 방식**이다.
2. **Jackson 충돌 위험 없음.** Spring Boot 3.3.5 BOM이 Jackson 전체를 2.17.2로
   고정하고, `openai-java-core`가 요구하는 최소 버전은 2.13.4다. SDK의 Jackson
   호환성 검사를 끄지 않는다.
3. **`jackson-module-kotlin`이 runtime scope로 들어온다.** Kotlin 표준 라이브러리가
   함께 딸려온다. 현재 4.45.0에서도 이미 그렇고 문제를 일으키지 않았다.
4. **`victools jsonschema-module-jackson:4.38.0`이 Java 클래스 → JSON Schema
   생성기다.** 4.47.0에서 이 버전이 올라가면 생성 스키마의 세부(예: `Optional<T>`
   표현, `additionalProperties` 위치)가 달라질 수 있다. 이것이 이번 승격의
   **유일한 실질 위험**이며, drift test(M0)가 이를 잡는 장치다.
5. **`.fromEnv()` 사용 주의.** v7-2 §10.4 예제는
   `OpenAIOkHttpClient.builder().fromEnv()`를 쓴다. `fromEnv()`는 `OPENAI_API_KEY`
   환경변수가 없으면 **Bean 생성 시점에 예외를 던져 Spring context 기동을
   막는다**. 현재 `OpenAiReportConfiguration`은 `apiKey.isBlank() ?
   "not-configured" : apiKey`로 이를 피하고 있고, 그래서 API 키 없이도
   `@SpringBootTest`가 뜬다. v7-2 §17 SDK smoke test가 "실제 API 키 없이
   실행"을 요구하므로 **현재의 명시적 `.apiKey(...)` 방식을 유지**하고
   `fromEnv()`는 쓰지 않는다.
6. **재시도 정책 변경**: 현재 `maxRetries(0)`, v7-2 §10.4는 `maxRetries(1)`.
   SDK가 연결 오류·408·409·429·5xx를 자동 재시도하므로 애플리케이션이 중첩
   재시도를 추가하지 않는다. 현재 timeout 기본값도 `90s`(application.properties)
   → v7-2는 `45s`.
7. **설정 키는 이동하지 않는다 (D6 확정)**: 기존 `app.ai-report.*` prefix와
   기존 환경변수(`AI_REPORT_ENABLED`, `OPENAI_API_KEY`, `OPENAI_MODEL`,
   `OPENAI_REQUEST_TIMEOUT`)를 유지한다. 배포 환경변수 변경이 없으므로
   `infra/single-ec2/compose.yml`과 GitHub Actions secrets를 건드리지 않는다.
   바뀌는 것은 값뿐이다: request-timeout `90s` → `45s`, maxRetries `0` → `1`.
8. **모델 기본값 제거**: 현재 `app.ai-report.model=${OPENAI_MODEL:gpt-5.6-luna}`.
   v7-2 §10.2는 `OPENAI_REPORT_MODEL=<validated-model-id>`이고 기본값이 비어
   있다(`model: ${OPENAI_REPORT_MODEL:}`). 모델 ID를 코드 상수로 두지 않는다.
9. **EOL Starter 부재 확인 완료**: `openai-java-spring-boot-starter`는 현재
   의존성 트리에 없다. 추가하지 않는다.

### 승격 검증 명령 (M1)

    cd backend
    ./mvnw --batch-mode dependency:tree -Dincludes=com.openai
    # 기대: openai-java가 backend/pom.xml 지정 버전으로 단 하나.
    #       EOL spring-boot-starter 없음.

    ./mvnw --batch-mode dependency:tree -Dincludes=com.fasterxml.jackson.core
    # 기대: jackson-core / jackson-databind / jackson-annotations 모두 2.17.2

    ./mvnw --batch-mode test -Dtest=OpenAiClientSmokeTest
    # 기대: OPENAI_API_KEY 없이 Spring context 기동, OpenAIClient Bean 1개


## Confirmed Product Decisions (D1~D7)

**2026-07-31 PM 확정. 구현 중 임의로 바꾸지 않는다.** 변경이 필요하다고
판단되면 구현을 멈추고 계획 변경 승인을 받는다.

이 결정들은 `docs/spec/AiWeeklyReport.md`에 반영되었다(수정 이력 D1~D7 절,
§4.3, §4.8, §4.9, §6.2, §6.3, §9.3, §10.2~§10.4, §10.6, §12.1, §12.2, §12.4,
§13, §15, §18, §21). **JSON Schema 두 개는 변경하지 않았다.**

- **D1 — `analysisStatus`**: JSON Schema 기준
  `NORMAL / PARTIAL / NO_ACTION_REQUIRED`. `COMPLETE`는 폐기.
  영향 범위: 계약 클래스 enum, drift test, fallback factory, renderer.

- **D2 — `achievement`**: **필수 객체**. 성과가 없으면 `status = NONE`,
  `headline`·`summary`는 빈 문자열, `evidenceTaskRefs`는 빈 배열.
  `Optional<T>`는 Schema가 nullable로 정의한 필드(`deadline.referenceRef`)에만
  쓴다.

- **D2 — `safeLabel` 개인정보 규칙 (가장 중요한 보안 결정)**:
  **원본 업무 제목·일정 제목을 OpenAI에 전송하지 않는다.** `safeLabel`은
  서버가 상태·담당·마감·체크리스트·보류 신호에서 조합한 **비식별 의미 유형
  문장**이며 원본 제목의 어떤 조각도 포함하지 않는다.

      허용: 승인 후 담당자가 없는 지연 업무
      허용: 외부 의존성으로 보류 중인 발표 준비 업무
      허용: 체크리스트가 시작되지 않은 임박 업무
      금지: 사용자 테스트 결과 반영
      금지: 고객사 A 계약서 수정
      금지: 김민준 발표 자료 최종 검토

  실제 제목과 이름은 분석이 끝난 뒤 서버 renderer가 `taskRef`/`eventRef`/
  `memberRef`로 재결합해 사용자 화면과 PDF에만 표시한다.
  결과적으로 **기존 구현의 개인정보 경계가 그대로 유지된다.** 위
  `Surprises & Discoveries`에 적은 "업무 제목 외부 전송 확대" 우려는 이
  결정으로 해소되었고, 위험 R1은 완화 상태로 내려간다.

- **D3 — 기간**: 그룹 시간대 기준 **완료된 월요일~일요일**,
  `[from, toExclusive)` 정확히 7일. 달 기준 주차(1·8·15·22·29일)와 월말 절단
  주차는 폐기한다. 대시보드 기간 필터와 AI 리포트의 "주차" 정의가 달라지는
  UX 영향을 감수한다.

- **D4 — lifecycle**: 초안 편집·수동 확정 제거. 생성 성공 또는 fallback 성공
  시 즉시 `FINALIZED`. 재생성은 유지하며 기존 revision을 덮어쓰지 않는다.

      최초 생성          → revision 1 FINALIZED
      regenerate=true    → revision 2 FINALIZED
      동일 source 재요청 → 기존 FINALIZED 반환 (OpenAI 재호출 0회)

  삭제 확정: `PATCH /{id}/draft`, `POST /{id}/finalization`, `GET /revisions`,
  `editorVersion`, `DRAFT`, `PublicationStatus`, `AiReportDraftEditor`.
  팀원 가시성 게이트 `AI_REPORT_NOT_FINALIZED`도 함께 사라진다.

- **D5 — 저장소**: `V34__create_ai_weekly_report_revision.sql`로 신규 테이블
  추가. **기존 `reports` 테이블의 스키마와 데이터를 변환하지 않는다.**
  기존 AI 리포트 ID 접근 시 `410 AI_REPORT_LEGACY_REVISION`.

- **D6 — 설정**: **이름을 바꾸지 않는다.** 기존 `app.ai-report.*` prefix와
  기존 환경변수(`AI_REPORT_ENABLED`, `OPENAI_API_KEY`, `OPENAI_MODEL`,
  `OPENAI_REQUEST_TIMEOUT`)를 유지한다. 이번 교체에서 바뀌는 값은 다음뿐이다.

      request-timeout    90s → 45s
      maxRetries           0 → 1
      store(false)             유지

  `OpenAIOkHttpClient.builder().fromEnv()`는 쓰지 않는다. 키가 없으면 Bean
  생성 시점에 Spring context 기동이 막히기 때문이다(위험 R5). 명시적
  `.apiKey(...)` + blank fallback을 유지한다.

- **D7 — 다운로드**: **실제 PDF 유지.** 기존 endpoint와 Content-Type을
  그대로 쓴다.

      GET /api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/pdf
      Content-Type: application/pdf
      Cache-Control: private, no-store

  `OpenHtmlReportPdfRenderer`를 재사용하고 `renderWeeklyAi()` 본문만 v7-2
  4페이지로 교체한다. `renderBasic()`은 손대지 않는다. HTML 다운로드로
  후퇴하지 않는다.


## Plan of Work

D1~D7은 2026-07-31에 확정되었고 `docs/spec/AiWeeklyReport.md`에 반영되었다.

**실행 규칙**: 한 채팅 = 한 milestone = 한 검증 가능한 결과 = 가능하면 한
커밋. milestone을 끝낸 뒤 검증·리뷰를 거쳐야 다음으로 넘어간다.
`M0~M11을 구현해` 같은 지시는 사용하지 않는다.

**선행 조건**: M1 착수 전에 위험 R9의 작업 트리 격리를 완료한다.

아래 순서로 진행한다. v7-2 §19의 구현 순서를 따르되,
"OpenAI 연결보다 Fallback·Fake Gateway·Validator를 먼저"라는 원칙을 지킨다.
그래야 API 키 없이도 전체 흐름과 4페이지 출력을 검증할 수 있다.

계약을 먼저 코드 밖에 고정한다(M0). 그다음 SDK를 승격하고 클라이언트 설정을
infrastructure로 격리한다(M1). 이어서 서버가 사실을 만드는 쪽 —
Snapshot assembler(M2)와 policy engine(M3) — 을 만들고, 그 위에 AI 없이도
동작하는 fallback과 의미 검증(M4)을 얹는다. 이 시점에 이미 "AI 없는 v7-2
리포트"가 메모리 상에서 완성된다. 그다음 저장(M5)과 포트/Fake Gateway
통합 테스트(M6)로 흐름을 DB까지 연결하고, 마지막에 실제 SDK adapter(M7)를
끼운다. API(M8)와 렌더러(M9), 프론트(M10)를 교체한 뒤, legacy 코드를
제거하고 diff를 검토한다(M11).

각 milestone은 독립적으로 검증 가능하며, 실패하면 다음으로 넘어가지 않는다.


## Milestones

### M0 — 계약 기준선 고정

목표: 코드를 쓰기 전에 v7-2 계약을 테스트 리소스로 못 박는다.

작업: `docs/contracts/`의 두 Schema를
`backend/src/main/resources/ai/`로 복사한다(런타임 검증·drift test용).
`backend/src/test/resources/ai/ai-weekly-report-snapshot-v1.example.json`와
`...-analysis-v1.example.json` fixture를 작성하고, 각 fixture가 대응 Schema를
통과함을 단언하는 `AiWeeklyReportSchemaFixtureTest`를 만든다. JSON Schema
검증 라이브러리가 없으면 `com.networknt:json-schema-validator`를 test scope로
추가한다(현재 미포함).

이후 존재하는 것: 어떤 Java 코드에도 의존하지 않는, 기계 검증 가능한 계약
기준선. Schema를 실수로 바꾸면 이 테스트가 깨진다.

검증: `cd backend && ./mvnw --batch-mode test -Dtest=AiWeeklyReportSchemaFixtureTest`
→ 통과.

수용: fixture 2개가 각 Schema를 통과하고, 일부러 필드를 하나 빼면 실패한다.

**결과 (2026-07-31 완료)**

    Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
    -- in com.teamproject.report.AiWeeklyReportSchemaFixtureTest

8건이 고정하는 계약:

1. Snapshot 예시가 `ai-weekly-report-snapshot.v1`을 만족한다
2. Analysis 예시가 `ai-weekly-report-analysis.v1`을 만족한다
3. Snapshot 계약이 필수 필드(`reportContext`) 누락을 거부한다
4. Analysis 계약이 필수 필드(`achievement`) 누락을 거부한다
5. Analysis 계약이 폐기된 `analysisStatus: "COMPLETE"`를 거부한다 (D1 회귀 방지)
6. Analysis 계약이 4번째 issue를 거부한다 (`maxItems: 3`)
7. Snapshot 계약이 계약에 없는 최상위 키 `policy`를 거부한다
   (`additionalProperties: false`)
8. 런타임 Schema 사본이 `docs/contracts` 정본과 바이트 단위로 동일하다

**mutation 검증**: 검증기가 살아 있는지 확인하기 위해 fixture의
`reportContext.period.from`을 `"2026-07-20"`에서 `"2026/07/20"`으로 일부러
깨뜨렸다. `snapshotExampleSatisfiesItsContract`가 실패했고, 이는
`formatAssertionsEnabled(true)` 설정이 실제로 `date` format을 검사한다는
증거다. 복구 후 다시 8/8 통과했다.

**의존성**: `com.networknt:json-schema-validator:1.5.2` (test scope). 저장소에
JSON Schema 검증 라이브러리가 없었다. `dependency:tree`로 단일 트리 확인.

**회귀 delta 0**: M0 적용 전후로 전체 스위트의 실패 집합이 동일하다
(176 tests, 6 failures). 아래 `기존 실패 baseline` 참조.

**fixture 내용의 D 결정 반영**: `analysisStatus: "NORMAL"`(D1),
`achievement.status: "AVAILABLE"`(D2), `safeLabel`은 비식별 의미 라벨만
사용(D2 — "승인 후 담당자가 없는 지연 업무" 등), 기간은
`2026-07-20`(월) ~ `2026-07-27` 배타 7일(D3).

### M1 — OpenAI SDK 클라이언트 설정 격리와 실행 정책 고정

목표: `OpenAIClient` Bean 설정을 `infrastructure/openai` 패키지로 격리하고
v7-2 실행 정책(timeout 45초, maxRetries 1, responseValidation)을 테스트로
고정한다.

**버전 승격은 이 milestone의 범위가 아니다.** 초안은 `4.45.0 → 4.47.0`
승격을 M1에 넣었으나, `4.47.0`은 Maven Central에 배포되어 있지 않아 채택할 수
없다. 아래 `SDK 버전 채택 게이트` 참조. `backend/pom.xml`은 변경하지 않는다.

작업: `infrastructure/openai/OpenAiReportProperties.java`(§10.3)와
`infrastructure/openai/OpenAIConfiguration.java`(§10.4)를 추가하고 기존
`infrastructure/OpenAiReportConfiguration.java`를 제거한다. Bean 이름
`openAiReportClient`는 유지한다 — 기존 adapter가
`@Qualifier("openAiReportClient")`로 주입받고 있으며 adapter production 코드는
이 단계에서 건드리지 않는다.

정책: `requestTimeout` 기본 45초, `maxRetries` 1, `responseValidation(true)`,
명시적 `.apiKey(...)` + blank fallback (D6 — `fromEnv()` 금지. 키가 없어도
Spring context가 기동해야 한다).

**D6 확정에 따라 설정 키 이름은 바꾸지 않는다.** `application.properties`의
`app.ai-report.*` prefix와 기존 환경변수를 그대로 쓰고, 값만 조정한다:
`app.ai-report.request-timeout`을 `90s` → `45s`,
`base-url`은 기존 지원을 유지한다(테스트·로컬 프록시, 사내 API Gateway, 호환
endpoint, 관측용 중계 계층에서 필요하다).
`max-retries`는 `OpenAiReportProperties` 기본값으로 제공하고 고정 행을 추가하지
않는다. `max-output-tokens`와 `prompt-version`은 M7로 미룬다(위 편차 참조).
`infra/single-ec2/compose.yml`과 GitHub Actions secrets는 **변경하지 않는다**.

`OpenAiReportConfigurationTest`를 추가한다: API 키 없이 context 기동,
`openAiReportClient` Bean 1개, properties 바인딩과 기본값, timeout·maxRetries
적용.

이후 존재하는 것: SDK 의존이 `infrastructure.openai` 아래로 모이고 실행 정책이
테스트로 고정된다.

검증:

    cd backend
    ./mvnw --batch-mode dependency:tree -Dincludes=com.openai
    ./mvnw --batch-mode dependency:tree -Dincludes=com.fasterxml.jackson.core
    ./mvnw --batch-mode test -Dtest=OpenAiReportConfigurationTest

수용: 트리에 `openai-java`가 `backend/pom.xml` 지정 버전으로 하나만, EOL
Starter 없음, Jackson 2.17.2, configuration test 통과, adapter test 9/9와
M0 test 8/8 유지.

**결과 (2026-07-31 완료)**

- `openai-java:4.45.0` 단일 경로, EOL Starter 없음, Jackson 2.17.2
- timeout 기본값 45초
- `maxRetries` 기본값 1, 명시적 `0` 허용, 허용 범위 0~3
- `responseValidation(true)` 활성화
- API 키 없이 Spring context 기동, `openAiReportClient` Bean 1개
- 기존 `app.ai-report.*`와 `base-url` 유지, 환경변수 이름 불변
- `OpenAiReportConfigurationTest` 9/9
- `OpenAiResponsesNarrativeAdapterTest` 9/9, `AiWeeklyReportSchemaFixtureTest` 8/8
- 전체 스위트 188건, 실패 1, 오류 0. 신규 회귀 0
- 잔존 실패는 study734/WorkTaskFlow#4 1건뿐

**편차 (M7로 이동)**

- `app.ai-report.max-output-tokens`: 현재 adapter가 `5000`을 직접 쓰므로 설정을
  추가해도 동작하지 않는 dead configuration이 된다. M1 범위 밖. M7에서 신규
  gateway 계약과 함께 설정화 여부를 결정한다.
- `app.ai-report.prompt-version`: M7에서 prompt resource가 도입될 때 추가한다.
  그 전까지 소비자가 없다.
- `app.ai-report.max-retries`: `OpenAiReportProperties` 기본값으로 1을 제공하고
  외부 설정이 있을 때만 덮어쓴다. `application.properties`에 불필요한 고정 행을
  추가하지 않는다.

사용되지 않는 설정을 미리 추가하지 않는다. 운영자에게 없는 제어 가능성을
약속하기 때문이다.

#### SDK 버전 채택 게이트

버전 승격은 **Maven Central resolution 성공**을 전제로 한다. 승격 전에 반드시
아래를 통과시키고, 실패하면 어떤 파일도 수정하지 않고 중단한다.

    cd backend
    ./mvnw --batch-mode dependency:get -Dartifact=com.openai:openai-java:<version>

2026-07-31 실행 결과:

    [ERROR] Could not find artifact com.openai:openai-java:jar:4.47.0 in central
            (https://repo.maven.apache.org/maven2)

Maven Central 메타데이터의 `latest`/`release`가 모두 `4.45.0`(2026-07-23 배포)
이고 `4.46.x`·`4.47.0`은 배포되어 있지 않다. 공식 GitHub에는 `v4.47.0` 릴리스가
있으나 Maven 아티팩트가 없어 재현 가능한 버전이 아니다. 게이트 실패에 따라
**아무 파일도 수정하지 않고 중단했고**, M1에서 버전 승격을 제거했다.
Central에 배포되면 이 게이트를 통과시킨 뒤 별도 작업으로 승격한다.

### M2 — Snapshot assembler와 bulk evidence query

목표: 서버가 v7-2 Snapshot을 만든다. OpenAI는 아직 등장하지 않는다.

작업: `application/dto/AiWeeklyReportDtos.java`에 `AiWeeklyReportSnapshotV1`
레코드 트리를 정의한다(Schema와 1:1). `AiWeeklyReportSnapshotAssembler`와
`infrastructure/TaskEvidenceQueryRepository`를 추가한다. 후자는 taskId 목록
기반 bulk 조회만 한다(§3 "업무별 개별 조회를 반복하지 않는다"). 체크리스트
집계, 댓글 수, 미해결 멘션 수, 자료 연결 수, 마지막 상태 전환, 보류 category,
재오픈 횟수, 캘린더 연결을 각각 1회 쿼리로 가져온다.
`TaskMetricsSnapshotSource`의 `ActivitySnapshot`·`dueState`·이전 기간 비교
로직을 이식하되 evidence key 체계와 등급 계산은 가져오지 않는다.

이후 존재하는 것: 그룹·기간을 주면 Schema를 만족하는 Snapshot이 나온다.

검증: `./mvnw --batch-mode test -Dtest=AiWeeklyReportSnapshotAssemblerTest`
- 실제 DB fixture로 assemble한 Snapshot을 직렬화해 M0의 Schema로 검증한다.
- 직렬화 문자열에 fixture의 실명·댓글 원문·업무 description이 **없음**을 단언한다.
- `members ≤ 100`, `tasks ≤ 100`, `calendarConstraints ≤ 30` 절단을 단언한다.
- baseline이 없을 때 `comparison.status == "NO_BASELINE"`이고 delta가 전부
  null임을 단언한다.
- N+1 방지: `@DataJpaTest` + Hibernate statistics로 업무 수와 무관하게 쿼리
  개수가 상수임을 단언한다.

수용: 위 5개 단언이 모두 통과한다.

**결과 (2026-07-31 완료, Bundle 1)**

M2a `feat(report): add v7-2 snapshot contract and safe labels` (`aa15660`)

- `application/dto/AiWeeklyReportDtos`: Schema와 1:1인 record 트리 + enum 10종
- `application/AiWeeklyReportSafeLabelFactory`: D2 비식별 라벨.
  **taskLabel/eventLabel이 제목을 인자로 받지 않는다.** 원문 유입을 시그니처
  수준에서 막는 것이라 문자열 검사보다 강한 보장이다.
- `AiWeeklyReportSnapshotContractTest` 7건 + `AiWeeklyReportSafeLabelFactoryTest`
  22건 = 29/29

M2b `feat(report): add bulk evidence queries for weekly snapshot` (`e7696d8`)

- `application/AiWeeklyReportEvidenceQuery`(port) +
  `infrastructure/TaskEvidenceQueryRepository`(impl)
- group by JPQL 3개(댓글 / 미해결 멘션 / 자료)로 업무 수와 무관하게 고정 쿼리
- 미해결 멘션은 도메인에 resolved 플래그가 없어 관측 사실로 판정한다. 멘션
  대상자가 그 멘션 이후 해당 업무에 댓글을 달지 않았으면 미해결로 본다.
- `AiWeeklyReportEvidenceQueryTest` 6/6. 업무 3건과 20건 모두 쿼리 3회,
  빈 목록은 0회

M2c `feat(report): assemble v7-2 weekly report snapshot`

- `application/AiWeeklyReportSnapshotAssembler`
- 기존 `TaskMetricsSnapshotSource`는 건드리지 않는다. 같은
  `TaskReportDataQuery`를 쓰되 별도 경로로 조립한다.
- `AiWeeklyReportSnapshotAssemblerTest` 10/10. 조립 결과를 M0 Schema로 검증하고
  직렬화 문자열에 제목·댓글 원문·실명이 없음을 단언한다.

**편차 — M3로 이월**

`tasks[].signalCodes`, `tasks[].allowed*Codes`, `riskCandidates`는 빈 배열로
둔다. 대응표 4가 이 셋을 policy engine 산출로 지정했고 이번 Bundle은 M3 위험
정책 구현이 금지되었기 때문이다. Schema는 빈 배열을 허용하므로 계약은 유효하다.
M3에서 채운다.

**보완이 필요한 파생 규칙 (M3 이후 재검토)**

- 업무와 캘린더 일정 사이에 도메인 관계가 없다. `relatedTaskRefs`와
  `calendarEventRefs`는 "업무 마감이 일정 구간 안에 들어오면 연결"이라는 시간
  겹침 규칙으로 파생했다. 명시적 연결 테이블이 생기면 교체한다.
- `members[].upcomingCalendarCount`는 일정 참석자 테이블이 없어 "그 팀원이
  만든 일정 중 기간 종료 이후 시작"으로 계산한다.

### M3 — Policy engine (risk candidate 생성)

목표: 서버가 위험 후보를 확정한다. AI가 위험을 자유 생성하지 못하게 하는 핵심.

작업: `application/AiWeeklyReportPolicyEngine`이 Snapshot tasks/members/
calendar에서 v7-2 §5.2의 위험 코드를 산출하고, 각 후보에
`evidenceCodes`(15종 enum 부분집합), `allowedOptionCodes`,
`allowedExecutionStepCodes`, `allowedCompletionSignalCodes`를 채운다.
severity precedence 정렬 후 상위 10개만 남긴다.
`RESOURCE_MISSING`은 명시적 requirement가 있을 때만 생성한다(§5.2 단서).

검증: `-Dtest=AiWeeklyReportPolicyEngineTest`
- 각 위험 코드가 정확한 조건에서만 생성됨.
- 모든 `evidenceCodes`/`allowed*Codes`가 Schema enum의 부분집합.
- severity 정렬과 10개 절단.
- 업무가 없으면 후보 0개.

### M4 — Deterministic fallback과 business validator

목표: OpenAI 없이도 완결된 v7-2 분석이 나오고, 어떤 분석이든 의미 검증을 받는다.

작업: `AiWeeklyReportAnalysisValidator`(§7 전체)와
`AiWeeklyReportFallbackFactory`(§16 규칙 1~6)를 추가한다. fallback은 후보를
precedence로 최대 3개 골라 template registry로 title·impact·decision을 만들고,
성과는 완료 + 체크리스트 완료 업무에서 최대 1개, baseline 없으면 delta 문장을
쓰지 않으며, 근거 부족은 `missingEvidence`로 표시한다.

검증: `-Dtest=AiWeeklyReportAnalysisValidatorTest,AiWeeklyReportFallbackFactoryTest`
- fallback 출력이 M0 Schema를 통과한다.
- fallback 출력이 Validator를 통과한다.
- Validator가 다음을 각각 거부한다: 존재하지 않는 taskRef, 허용 목록 밖
  option code, `[P2]`·`[P1,P1]`·`[P1,P3]` 우선순위, NO_BASELINE인데 delta
  metricRef, `HIGH` + `missingEvidence` 동시 존재, 완료가 아닌 업무를 성과로
  인용, `LEADER_DECISION_REQUIRED`가 아닌데 `referenceRef`가 null.
- 업무가 없으면 `analysisStatus == NO_ACTION_REQUIRED`.

수용: 위 거부 케이스가 전부 실패로 잡힌다.

### M5 — Revision 저장과 legacy 정책

목표: 결과가 DB에 남고, legacy 행 정책이 테스트로 고정된다.

작업: `V34__create_ai_weekly_report_revision.sql`,
`domain/AiWeeklyReportRevision.java`, `AiWeeklyReportStatus`,
`AiAnalysisMode`, `infrastructure/AiWeeklyReportRevisionRepository`.
source hash 계산(§12.3: canonical snapshot JSON + prompt version +
analysis schema version + model)을 추가한다.
`MySqlFlywayMigrationTest`의 head를 `34`로 갱신한다.

검증:

    ./mvnw --batch-mode test -Dtest=MySqlFlywayMigrationTest
    ./mvnw --batch-mode test -Dtest=AiWeeklyReportLegacyPolicyTest

수용: 위 `대응표 2 — 검증` 항목 4가지가 모두 통과한다.

### M6 — Gateway 포트와 Fake Gateway 통합 테스트

목표: 실제 OpenAI 없이 전체 오케스트레이션을 검증한다.

작업: `application/port/AiWeeklyReportGateway`와
`AiWeeklyReportService`(§14 오케스트레이션 + 트랜잭션 경계 + group row lock
이식)를 추가한다. 테스트용 `FakeAiWeeklyReportGateway`를 만든다.

검증: `-Dtest=AiWeeklyReportServiceIntegrationTest` — v7-2 §17 통합 테스트
표 16행을 그대로 구현한다(정상 / timeout / rate limit / structured output 없음
/ 잘못된 taskRef / 허용되지 않은 option / P1 중복 / NO_BASELINE인데 delta /
업무 없음 / 동일 snapshot 재요청 / regenerate=true / 다운로드 /
raw comment·name·description 미전송 / AI가 새 날짜 작성 / OpenAI 비활성화 /
model 설정 없음).

수용: 16개 시나리오 전부 기대 결과와 일치. 특히 실패 시나리오 전부에서
`status=FINALIZED`, `analysis_mode=SERVER_FALLBACK`이고 사용자 응답이 200/201.

### M7 — 공식 SDK adapter

목표: 실제 `OpenAIClient`를 포트 뒤에 연결한다.

작업: `infrastructure/openai/OpenAiWeeklyReportGateway`(§10.7),
`OpenAiAnalysisContractMapper`, `OpenAiReportPrompt`(리소스
`ai/v7-2-prompt-001.txt` 로딩), `OpenAiReportException` 계층(§10.8).
기존 `OpenAiResponsesNarrativeAdapter`의 예외 분류·응답 탐색 패턴을 이식한다.
로그 redaction(§10.9): 예외 전문·Snapshot 전체·raw response를 남기지 않는다.

검증: `-Dtest=OpenAiWeeklyReportGatewayTest` — 기존 테스트의
`com.sun.net.httpserver` 로컬 스텁 서버를 재사용해 정상/refusal/incomplete/
malformed/429/5xx/timeout을 각각 올바른 예외 category로 매핑함을 단언한다.
실제 OpenAI API는 호출하지 않는다.

### M8 — API 계약 교체

목표: 엔드포인트가 v7-2 §9를 따른다.

작업: `presentation/AiWeeklyReportController`를 추가하고
`WeeklyReportController`를 제거한다. `POST` 요청 본문
`{from, toExclusive, language, regenerate}`, 응답
`{reportId, groupId, from, toExclusive, revision, status, analysisMode,
generatedAt, downloadUrl}`. HTTP 201/200/202. `GET /{reportId}` 조회.

**D7 확정**: 다운로드는 기존 경로 `GET /{reportId}/pdf`를 그대로 쓰고
`Content-Type: application/pdf`, `Cache-Control: private, no-store`,
파일명 `ai-weekly-report-{from}-r{revision}.pdf`를 반환한다. `/download`
경로와 HTML 응답은 만들지 않는다.

**D4 확정**: `PATCH /{id}/draft`, `POST /{id}/finalization`,
`GET /revisions`는 만들지 않는다. `POST`의 `regenerate:true`가 재생성을
대신한다.

**D5 확정**: `{reportId}`가 신규 테이블에 없고 기존 `reports`에 있으면
`410 Gone` + `AI_REPORT_LEGACY_REVISION`을 반환한다.

권한은 `WeeklyReportModule`의 유료 TEAM + LEADER/MEMBER 검사를 이식한다.
D4로 DRAFT가 사라지므로 `AI_REPORT_NOT_FINALIZED` 분기는 제거한다.

검증: `-Dtest=AiWeeklyReportApiTest` — 무료 그룹 403, 팀원 생성 403,
팀원 조회 200(확정 조작 없이 바로 조회 가능), 비멤버 403/404,
월요일이 아닌 `from` 400, 7일이 아닌 기간 400, 미완료 기간 400,
legacy id 410, PDF 다운로드의 Content-Type·Content-Disposition·Cache-Control,
다운로드가 gateway를 0회 호출.

### M9 — v7-2 4페이지 렌더러

목표: 저장된 revision이 4페이지 문서로 출력된다.

작업: `AiWeeklyReportViewProjector`(ref → 실제 label/숫자/날짜/이름 재결합,
§8)와 `OpenHtmlReportPdfRenderer.renderWeeklyAi` 재작성.
`renderBasic`은 손대지 않는다.

**D7 확정**: OpenHTMLtoPDF 기반 실제 PDF 출력을 유지한다. 폰트 로딩,
`@page A4`, `page-break-inside:avoid`, `html()` escape 유틸을 그대로 쓰고
`renderWeeklyAi()` 본문의 섹션 구성만 4페이지로 교체한다.

**D2 확정**: renderer는 `taskRef`/`eventRef`/`memberRef`로 실제 업무 제목·
일정 제목·팀원 이름을 서버 데이터에서 조회해 삽입한다. AI가 만든
`safeLabel`은 화면에 그대로 노출하지 않는다.

검증: `-Dtest=AiWeeklyReportHtmlRegressionTest` — v7-2 §17 HTML 회귀 목록:
A4 4페이지 역할, page 1 기본 스타일 불변, issue ≤ 3, 정상 팀원 전체표 없음,
`Structured Outputs`/모델 id/토큰 수 문자열이 본문에 없음, 모든 AI 카드에
근거 ref 표시, HTML escape, null 데이터 표시 규칙.

### M10 — 프론트엔드 교체

목표: 화면이 v7-2 4페이지를 보여주고 기술 용어를 노출하지 않는다.

작업: `reportApi.ts` 타입 교체(기본 리포트 함수는 유지),
`AiReportContent.tsx` 재작성, `reportProjection.ts` 삭제,
`AiReportDraftEditor.tsx` 삭제(D4), 패널·페이지 수정, E2E 재작성.

검증:

    npm --prefix frontend ci
    npm --prefix frontend run build
    # E2E는 아래 Validation 절의 실제 명령 참조 (test:e2e 스크립트는 존재하지 않음)

### M11 — legacy 제거와 최종 diff 검토

목표: 이중 계약이 남지 않고, diff에 v7-2와 무관한 변경이 없다.

작업: 아래 `제거할 legacy 코드 목록` 전체를 삭제한다. 그 뒤 전체 테스트와
빌드를 돌리고 diff를 파일 단위로 검토한다.

검증: `./mvnw --batch-mode test` 전체 통과, `npm --prefix frontend run build`
성공, `git diff --check` 무출력, `grep -rn "NarrativeContract\|ReportContracts\|
MemberPerformanceRule\|AiNarrativeGenerator" backend/src` 무출력.


## 제거할 legacy 코드 목록

M11에서 이 목록이 0이 되어야 한다.

백엔드 main (파일 삭제):

    backend/src/main/java/com/teamproject/report/application/AiNarrativeGenerator.java
    backend/src/main/java/com/teamproject/report/application/NarrativeContract.java
    backend/src/main/java/com/teamproject/report/application/ReportContracts.java
    backend/src/main/java/com/teamproject/report/application/MetricsSnapshotSource.java
    backend/src/main/java/com/teamproject/report/application/MemberPerformanceRule.java
    backend/src/main/java/com/teamproject/report/application/WeeklyReportGenerationModule.java
    backend/src/main/java/com/teamproject/report/infrastructure/OpenAiResponsesNarrativeAdapter.java
    backend/src/main/java/com/teamproject/report/infrastructure/TaskMetricsSnapshotSource.java
    backend/src/main/java/com/teamproject/report/presentation/WeeklyReportController.java

D4 확정에 따라 추가:

    backend/src/main/java/com/teamproject/report/application/WeeklyReportModule.java
        (권한 메서드만 새 서비스로 이식 후 삭제)

백엔드 main (부분 제거):

    OpenAiReportConfiguration.java        → infrastructure/openai/OpenAIConfiguration.java 로 이동 후 삭제
    ReportPeriod.java                     → 달 기준 절단 주차 로직 제거 (D3)
    WeeklyReport.java                     → 신규 생성 경로에서 분리 (엔티티는 legacy 데이터 보존용으로 유지)
    WeeklyReportRepository.java           → lease/budget/lock 쿼리 제거
    ReportPdfRenderer.java                → renderWeeklyAi 시그니처 교체
    OpenHtmlReportPdfRenderer.java        → renderWeeklyAi 본문(80~181행) 교체
    TaskFlowMetrics.java                  → v7-2 workflow 산출로 재작성
    application.properties                → app.ai-report.model의 하드코딩 기본값
                                            gpt-5.6-luna 제거, request-timeout 45s,
                                            max-retries/max-output-tokens/prompt-version 추가.
                                            prefix와 환경변수 이름은 유지 (D6)

백엔드 test (파일 삭제):

    backend/src/test/java/com/teamproject/report/NarrativeContractTest.java
    backend/src/test/java/com/teamproject/report/OpenAiResponsesNarrativeAdapterTest.java
    backend/src/test/java/com/teamproject/report/WeeklyReportModuleTest.java
    backend/src/test/java/com/teamproject/report/WeeklyReportLifecycleTest.java
    backend/src/test/java/com/teamproject/report/MemberPerformanceRuleTest.java
    backend/src/test/java/com/teamproject/report/TaskActivityMetricsSnapshotSourceTest.java

프론트엔드 (파일 삭제):

    frontend/src/features/report/reportProjection.ts
    frontend/src/features/report/components/AiReportDraftEditor.tsx        (D4)

프론트엔드 (타입·컴포넌트 제거):

    reportApi.ts    → LocalReference, EvidenceValue, NarrativeItemView,
                      RiskNarrativeItemView, ActionNarrativeItemView,
                      DecisionNarrativeItemView, ReportAnalysis,
                      NarrativeDraft*, ComparisonMetrics, MemberWorkView,
                      TaskWorkView, OperationalView, WeeklyAiReport.metrics/
                      evidence/analysis/draft/gradeRule,
                      findWeeklyAi, revisions, editDraft, regenerate, finalize
    AiReportContent.tsx → MemberPerformance, MemberExceptions,
                      MemberComparison, IndividualMemberDetail,
                      MatchedServerRiskTasks, EvidenceDetails,
                      ReportDensity 3단계, SUMMARY/STANDARD/EXTRA/FINALIZED_PDF
                      KPI 세트, projectAiRisks

개념 제거 (코드 전역에서 사라져야 하는 것):

- evidence key + `{{placeholder}}` 치환 계약 (`PLACEHOLDER` 정규식, `render()`)
- `MEMBER-`/`TASK-`/`GOAL-` 별칭의 본문 노출 금지 규칙 (v7-2는 구조화 ref만 사용)
- 숫자 리터럴 금지 정규식 검사 (v7-2는 AI 텍스트에 수치를 넣지 않는 구조로 대체)
- 팀원 등급 A~F, score, rank, `members.ratedCount`, `gradeRule`
- 서버 risk code `OVERDUE_PRESENT` / `ON_HOLD_PRESENT` / `HIGH_PRIORITY_PRESENT`
- `schema_version` v2/v3/v4 upcast 분기
- `prompt_version` `v8`
- report density (`SUMMARY`/`STANDARD`/`DETAILED`)와 projection scope
  (`GROUP`/`MEMBER_COMPARISON`/`INDIVIDUAL_MEMBER`) URL 상태


## Concrete Steps

모든 명령의 작업 디렉터리를 명시한다. Windows에서는 `./mvnw` 대신
`.\mvnw.cmd`를 쓴다(`AGENTS.md`).

착수 전 상태 확인 (저장소 루트):

    git status --short
    git remote -v
    git log --oneline -1

M1 착수 전 작업 트리 격리 (위험 R9, 권장안 = 별도 커밋):

    git add backend/pom.xml .github/workflows/ci.yml backend/Dockerfile
    git commit -m "chore: bump toolchain to Java 25"
    git add backend/src/main/java/com/teamproject/report/application/BasicReportAccessService.java
    git commit -m "fix(report): drop free-plan weekly download limit"
    git status --short
    # 기대: .serena/ 만 남는다 (?? 로 표시)

이 격리를 끝내야 `backend/pom.xml`의 SDK 버전 변경을 hunk 선택 없이 안전하게
커밋할 수 있다.

의존성 확인 (`backend/`):

    ./mvnw --batch-mode dependency:tree -Dincludes=com.openai
    ./mvnw --batch-mode dependency:tree -Dincludes=com.fasterxml.jackson.core

단위 테스트 (한 milestone 범위, `backend/`):

    ./mvnw --batch-mode test -Dtest=<TestClassName>

백엔드 전체 (`backend/`):

    ./mvnw --batch-mode test

프론트엔드 (저장소 루트):

    npm --prefix frontend ci
    npm --prefix frontend run build

E2E — **Issue #2에 적힌 `npm --prefix frontend run test:e2e`는 존재하지 않는
스크립트다.** 실제로 실행하려면 다음이 필요하다:

1. `@playwright/test`를 `frontend/package.json`의 `devDependencies`에 추가하고
   `package-lock.json`을 갱신한다(현재 둘 다 누락. `npm ci`가 로컬
   `node_modules/@playwright`를 지운다).
2. `frontend/package.json`에 스크립트를 추가한다:
   `"test:e2e": "playwright test"`.
3. 백엔드가 떠 있어야 한다(`backend/`에서 `./mvnw spring-boot:run`).
4. 그 뒤 저장소 루트에서:

       npm --prefix frontend run test:e2e -- ai-weekly-report.chrome.spec.ts

   또는 스크립트 추가 없이 `frontend/`에서:

       npx playwright test ai-weekly-report.chrome.spec.ts --project=chrome

1~2번은 v7-2와 무관한 인프라 변경이므로 **별도 커밋 또는 별도 이슈로 분리**한다.
Issue #2 Done criteria "최종 diff에 v7-2와 무관한 변경이 없다"와 충돌하기
때문이다.

diff 검토 (저장소 루트):

    git diff --check
    git diff --name-only
    git diff --stat


## Validation and Acceptance

### 정본 교정 + ExecPlan 커밋의 수용 조건 (2026-07-31)

커밋은 **두 개로 분리**한다.

1. 명세 정본 교정 — `docs/spec/AiWeeklyReport.md` 하나만
2. ExecPlan — `.agent/exec-plans/ai-weekly-report-v7-2-replacement.md` 하나만

`.gitignore:11`이 `*.md`를 무시하므로 ExecPlan은 `git add -f`가 필요하다
(`AGENTS.md`가 `.gitignore` 수정을 금지한다. 기존 exec-plan 2개도 같은
방식으로 추적 중이다).

    git add docs/spec/AiWeeklyReport.md
    git diff --cached --name-only     # docs/spec/AiWeeklyReport.md 한 줄
    git diff --cached --check
    git commit -m "docs(report): apply D1-D7 product decisions to v7-2 spec"

    git add -f .agent/exec-plans/ai-weekly-report-v7-2-replacement.md
    git diff --cached --name-only     # ExecPlan 한 줄
    git diff --cached --check
    git commit -m "docs(agent): confirm D1-D7 in the v7-2 replacement ExecPlan"

수용 조건:

- [ ] `docs/contracts/*.schema.json`이 두 커밋 어디에도 포함되지 않는다.
- [ ] `backend/src`, `frontend/src` 아래 어떤 파일도 변경되지 않는다.
- [ ] 두 커밋 각각의 `git diff --cached --name-only`가 정확히 한 줄이다.
- [ ] `git diff --check` 출력이 없다.
- [ ] Java 25 승격과 `BasicReportAccessService` 변경은 두 커밋에 섞이지 않고
      작업 트리에 그대로 남는다(또는 별도 커밋으로 분리된다).

### (참고) 최초 ExecPlan 초안 커밋의 수용 조건

- [ ] `.agent/exec-plans/ai-weekly-report-v7-2-replacement.md`가 존재하고,
      Issue #2가 요구한 10개 항목(파일별 대응표 / revision migration 정책 /
      권한·lifecycle 유지 여부 / Snapshot·Analysis 적용 방법 / SDK 4.45→4.47
      영향 / 단계 분할 / 단계별 테스트 명령과 완료 조건 / legacy 제거 목록 /
      위험 / Done criteria 대응표)을 모두 담는다.
- [ ] `git diff --check` 출력이 없다 (공백 오류 없음).
- [ ] `git diff --name-only`가 아래 4개 **기존 미커밋 파일만** 보여주고,
      새 ExecPlan은 추적되지 않은 파일로 `git status --short`에 `??`로 나타난다:

          .github/workflows/ci.yml
          backend/Dockerfile
          backend/pom.xml
          backend/src/main/java/com/teamproject/report/application/BasicReportAccessService.java

      **주의**: 요청된 검증 "ExecPlan 파일만 변경됐는지 확인"은 이 4개
      기존 변경 때문에 문자 그대로는 성립하지 않는다. 이 변경들은 이 작업이
      시작되기 전부터 있었고 `AGENTS.md`가 보존을 요구한다. 커밋할 때는
      `git add .agent/exec-plans/ai-weekly-report-v7-2-replacement.md` 하나만
      스테이징해 `git diff --cached --name-only`가 그 한 줄만 출력하는 것으로
      의도를 확인한다.
- [ ] `docs/spec/AiWeeklyReport.md`와 `docs/contracts/*.schema.json`이
      `git status`에 나타나지 않는다.
- [ ] `backend/src`, `frontend/src` 아래 어떤 파일도 이 작업으로 변경되지 않는다.

### 구현 전체의 수용 조건 (M11 종료 시점)

- [ ] `cd backend && ./mvnw --batch-mode test` 전체 통과.
- [ ] `./mvnw --batch-mode dependency:tree -Dincludes=com.openai`가
      `openai-java`를 `backend/pom.xml` 지정 버전으로 하나만 출력하고
      EOL Starter가 없다.
- [ ] `npm --prefix frontend ci && npm --prefix frontend run build` 성공.
- [ ] `grep -rn "NarrativeContract\|ReportContracts\|MemberPerformanceRule" backend/src`
      무출력.
- [ ] 팀장 계정 수동 시나리오: 완료된 7일 기간 생성 → 4페이지 확인 →
      다운로드 → 같은 기간 재요청 시 OpenAI 재호출 0회.
- [ ] `OPENAI_REPORT_ENABLED=false`로 같은 시나리오 → FINALIZED +
      `analysisMode=SERVER_FALLBACK`, 하단 "기본 분석으로 생성됨".
- [ ] 실제 API 키로 1건 수동 검증(별도 프로파일, CI 필수 테스트 아님).


## Done criteria 대응표 (Issue #2)

| Issue #2 Done criteria | 대응 milestone | 검증 방법 | 상태 |
|---|---|---|---|
| `docs/spec/AiWeeklyReport.md`가 v7-2 단일 정본으로 유지된다 | 전 구간 | `git status`에 이 파일이 나타나지 않음 | 이 커밋에서 충족 |
| 기존 계약과 새 계약이 동시에 활성 상태로 남아 있지 않다 | M11 | `제거할 legacy 코드 목록` 전부 삭제 + grep 무출력. legacy revision은 `410 Gone` | 계획됨 |
| Snapshot이 `ai-weekly-report-snapshot.v1`을 따른다 | M0, M2 | `AiWeeklyReportSnapshotAssemblerTest`가 직렬화 결과를 Schema로 검증 | 계획됨 |
| OpenAI 출력이 `ai-weekly-report-analysis.v1`을 따른다 | M0, M7 | drift test + `OpenAiWeeklyReportGatewayTest` | 계획됨. D1/D2 확정 완료 |
| 공식 OpenAI Java SDK와 Responses API를 사용한다 | M1, M7 | dependency:tree + `OpenAiClientSmokeTest`(ArchUnit/정적 import 검사 포함) | 계획됨 |
| 모든 AI 판단이 실제 candidate 및 task ref에 연결된다 | M4 | `AiWeeklyReportAnalysisValidatorTest` §7.1/§7.2 케이스 | 계획됨 |
| AI가 입력에 없는 수치·날짜·업무·사람을 생성하지 못한다 | M4, M9 | Validator ref 검사 + §7.6 deadline 구조 + renderer 재결합 | 계획됨 |
| 의미 검증 실패 시 deterministic fallback이 사용된다 | M4, M6 | 통합 테스트 16 시나리오 중 실패 8건이 `SERVER_FALLBACK` + `FINALIZED` | 계획됨 |
| 사용자 리포트가 v7-2 4페이지 정보 구조를 따른다 | M9, M10 | `AiWeeklyReportHtmlRegressionTest` + 신규 E2E. 출력 형식은 실제 PDF 유지 (D7) | 계획됨 |
| 기술 용어가 사용자 본문에 노출되지 않는다 | M9, M10 | HTML 회귀 테스트가 `Structured Outputs`/모델 id/token 문자열 부재를 단언 | 계획됨 |
| 정상 팀원 전체표와 불필요한 정상 상태 카드가 제거된다 | M10, M11 | `MemberPerformance`/`MemberExceptions` 삭제 + E2E | 계획됨 |
| 더 이상 사용되지 않는 계약·projection·renderer 코드가 제거된다 | M11 | `제거할 legacy 코드 목록` | 계획됨 |
| legacy revision 처리 정책이 테스트로 검증된다 | M5 | `AiWeeklyReportLegacyPolicyTest` 4개 단언 + Flyway head 34 | 계획됨. D5 확정 완료 |
| 백엔드 테스트가 통과한다 | M11 | `cd backend && ./mvnw --batch-mode test` | 계획됨 |
| 프론트엔드 build와 관련 E2E가 통과한다 | M10 | `npm --prefix frontend run build` 통과. **E2E는 스크립트·의존성 부재로 현재 실행 불가 — 위 `Concrete Steps` 참조** | **차단됨** |
| 최종 diff에 v7-2와 무관한 변경이 없다 | M11 | `git diff --name-only` 검토. 단 Java 25 승격·BasicReportAccessService 변경은 이 작업 이전부터 존재 | 부분 충족 불가 — 아래 위험 R9 |


## Risks — 데이터·보안·호환성

- **R1 (보안·개인정보, 높음 → 낮음, D2로 완화됨).** v7-2는
  `tasks[].safeLabel`과 `calendarConstraints[].safeLabel`을 필수로 요구하고,
  초안 예시는 원본 업무 제목을 그대로 담고 있었다. 현재 구현은 OpenAI에
  업무 제목을 전혀 보내지 않으므로 그대로 적용했다면 외부 LLM 제공자로의
  데이터 전송 범위가 확대됐을 것이다.
  **D2 확정으로 해소되었다**: `safeLabel`은 서버가 상태·담당·마감·체크리스트·
  보류 신호에서 조합한 비식별 의미 유형 문장이며 원본 제목 조각을 포함하지
  않는다. 개인정보 경계는 기존 구현과 동일하게 유지된다.
  남은 완화 조치: (a) safeLabel 생성기를 **화이트리스트 방식**(허용된 신호
  조합 템플릿에서만 문장 생성)으로 구현해 원문 유입 경로를 원천 차단,
  (b) 120/160자 상한 준수, (c) `store(false)` 유지, (d) M2에서 직렬화 결과에
  fixture 실명·제목·댓글 문자열이 없음을 단언하는 테스트를 필수로 둔다.
- **R2 (데이터, 높음).** `period_end`(포함) → `period_to_exclusive`(배타)
  의미 변경. legacy 행을 새 해석으로 읽으면 모든 리포트가 하루씩 어긋난다.
  완화: 새 테이블 분리(D5 권고안) + legacy 행 재해석 금지 + M5 테스트.
- **R3 (데이터, 높음).** `metrics_json`/`ai_summary_json`의 evidence key 체계는
  v7-2 Snapshot/Analysis로 **무손실 변환이 불가능하다**. in-place 변환은 사실상
  데이터 파기다. 완화: D5를 in-place로 결정하지 않는다. 결정된다면 사전 DB
  백업을 필수 절차로 명시한다.
- **R4 (호환성, 보류).** 승격 시 `victools jsonschema-module-jackson`이
  올라가면 SDK 생성 스키마 세부가 달라져 저장 Schema와 drift가 생길 수 있다.
  완화: M0의 drift test가 CI에서 항상 돌게 한다.
- **R5 (가용성, 중간).** `OpenAIOkHttpClient.builder().fromEnv()`는
  `OPENAI_API_KEY` 부재 시 Spring context 기동을 막는다. v7-2 §10.4 예제를
  그대로 복사하면 API 키 없는 개발·CI 환경이 전부 깨진다. 완화: 명시적
  `.apiKey(...)` + blank fallback 유지(현재 방식), M1 smoke test로 고정.
- **R6 (가용성, 중간 → 낮음, D6으로 완화됨).** 설정 키·환경변수 이름을
  바꿨다면 배포 환경(`infra/single-ec2/compose.yml`, GitHub Actions secrets,
  운영 `.env`)을 동시에 고쳐야 했고, 누락 시 배포 후 AI 리포트가 조용히
  fallback으로만 동작했을 것이다. **D6 확정으로 이름을 바꾸지 않으므로 이
  위험은 사라진다.** 남은 완화: `app.ai-report.enabled=true`인데 model이나
  api-key가 비어 있으면 기동 시 WARN 로그를 남기고, M8 API 테스트에
  "model 설정 없음" 시나리오를 포함한다.
- **R7 (기능 후퇴, 중간).** D4를 (a)로 결정하면 팀장 초안 편집·확정과
  팀원 가시성 제어가 사라진다. 이미 출시되어 E2E 5건이 지키던 기능이다.
  완화: 결정 근거와 영향 범위를 `Decision Log`에 남기고 이해관계자에게 알린다.
- **R8 (기능 후퇴, 해소됨).** D7이 "실제 PDF 유지"로 확정되었으므로
  다운로드 기능 후퇴는 발생하지 않는다. 구현 시 주의: v7-2 §9.3의 초안
  문구(`/download`, `text/html`)를 참고하지 않도록 명세를 이미 교정했다.
- **R9 (프로세스, 해소됨 — 2026-07-31).** 작업 트리에 있던 미커밋 변경 4개를
  파일별로 판정했다. **"전부 stash" 또는 "전부 커밋"은 둘 다 틀린 처리였다.**
  초안이 세 선택지(별도 커밋 / stash / 별도 worktree)를 동등하게 적은 것은
  잘못이며, 실제로는 파일마다 답이 달랐다.

  (1) `BasicReportAccessService.java` — **커밋해야 했다.** 이미 커밋되어 있던
      `GroupInvitationApiTest.freeAndPersonalReportsAreUnlimitedWhileGroupScopeStillRequiresLeader`
      가 FREE 그룹 리포트 무제한과 `remainingThisWeek` 부재를 요구하는데,
      HEAD의 구현은 여전히 주 2회 제한과 `remainingThisWeek`를 반환했다.
      stash하면 이 테스트가 깨진다. 무관한 WIP가 아니라 **커밋된 정책 테스트에
      빠져 있던 구현 조각**이었다.
      처리: baseline 복구 커밋 `a9fd3e3`
      (`fix(report): align basic report access with unlimited policy`).
      Issue #2 구현 범위가 아니라 착수 전 정합성 복구다.
      근거: 복원 후 `-Dtest=GroupInvitationApiTest` 7/7 통과.

  (2) `backend/pom.xml`의 `java.version=25`, `.github/workflows/ci.yml`,
      `backend/Dockerfile` — **트리에서 제외해야 했다.** 로컬 JDK가 21이라
      컴파일 자체가 불가능하다.

          [INFO] Compiling 194 source files with javac [release 25]
          [ERROR] Fatal error compiling: error: release version 25 not supported

      즉 원래 작업 트리는 빌드되지 않는 상태였고, 이 상태로는 M0~M11의
      어떤 검증도 로컬에서 할 수 없다.
      처리: 세 파일을 HEAD로 되돌렸다. 원본은 `stash@{0}`
      (`wip: unrelated changes before v7-2 M0`)에 보존되어 있고 삭제하지
      않는다. **v7-2 M0~M11에서는 이 stash를 사용하지 않는다.**
      현재 저장소 기준은 Spring Boot 3.3.5 + Java 21이며 v7-2 구현에 Java 25가
      필요한 이유가 없다. Java 25 승격이 실제 팀 결정이라면 별도 Issue·별도
      브랜치에서 JDK·CI·Docker를 동시에 검증해야 하는 독립 작업이다.

  잔여 규칙: v7-2 커밋에서 `backend/pom.xml`의 staged diff에 Java 버전 변경이나
  OpenAI SDK 버전 변경이 섞이지 않았는지 `git diff --cached`로 매번 확인한다.

- **R10 (검증 공백, 중간).** E2E가 CI 게이트가 아니고 Playwright가
  `package.json`/`package-lock.json`에 없다. v7-2 4페이지 구조를 지키는
  회귀 그물이 로컬에만 존재한다. 완화: 백엔드 HTML 회귀 테스트(M9)를
  1차 방어선으로 삼고, Playwright 의존성 정식화는 별도 이슈로 분리한다.
- **R11 (성능, 낮음).** v7-2 Snapshot은 업무별 댓글 수·멘션 수·자료 수·
  캘린더 연결을 새로 요구한다. 순진하게 구현하면 업무 100건에 400+ 쿼리다.
  완화: `TaskEvidenceQueryRepository`를 taskId 목록 기반 bulk 전용으로 두고
  M2에서 쿼리 개수 상수성을 테스트한다.
- **R12 (동시성, 낮음).** v7-2 spec에는 GENERATING 상태가 영구히 남는 경우의
  회복 절차가 없다. 현재 구현의 2분 lease + attempt 소유권 확인이 이를
  해결하고 있다. 완화: 이 패턴을 새 서비스로 이식하고 근거 주석을 남긴다.
- **R13 (계약, 해소됨).** D1·D2 확정과 명세 교정으로 계약 클래스와 drift
  test를 작성할 수 있게 되었다. 명세와 Schema가 일치하므로 M0을 시작할 수
  있다. 잔여 주의: 구현 중 명세와 Schema가 다시 어긋나 보이면 **Schema를
  고치지 말고** 작업을 멈추고 계획 변경 승인을 받는다.

- **R14 (프로세스, 중간).** 계획 전체를 한 번에 실행하면 작업 범위가
  폭발하고 중간 검증 지점이 사라진다. 완화: 한 채팅 = 한 milestone =
  한 검증 가능한 결과 = 가능하면 한 커밋. `M0~M11을 구현해` 같은 지시를
  금지한다(`Progress`의 실행 규칙 항목).

- **R15 (검증 공백, 해소됨 — 2026-07-31).**
  `OpenAiResponsesNarrativeAdapterTest` 5건이 `Unable to establish loopback
  connection`으로 전부 실패해 SDK 관련 변경의 회귀를 확인할 수단이 없었다.
  해소: `com.sun.net.httpserver.HttpServer`와 임의 포트 의존을 제거하고
  `OpenAIClient`·`ResponseService` 두 인터페이스만 Mockito로 대체했다. 응답은
  기존 wire JSON을 공식 SDK의 `Response`로 역직렬화한 뒤
  `StructuredResponse<T>`의 public 생성자로 감싸 만들므로, 중첩
  output/message/content 탐색·Structured Output 역직렬화·refusal 판별·usage
  집계는 여전히 SDK의 실제 구현이 수행한다. 시나리오별로 9개 테스트로 분리해
  9/9 통과하며 실제 OpenAI API는 호출하지 않는다.
  근거: 커밋 `f2f9feb`, study734/WorkTaskFlow#3 (closed).
  결과: 전체 스위트의 Errors가 5 → 0이 되었고 실패는
  study734/WorkTaskFlow#4 1건만 남는다.

- **R16 (계획 정확성, 해소됨 — 2026-07-31).** 기준 커밋 `bfca414`의 명세가
  `com.openai:openai-java:4.47.0`을 필수로 요구했으나 그 아티팩트는 Maven
  Central에 존재하지 않는다. 계획이 **실재하지 않는 버전을 완료 조건으로
  고정**하고 있었고, 그대로 두면 M1을 어떤 방식으로도 완료 처리할 수 없었다.
  해소: SDK 버전 정본을 `backend/pom.xml`로 옮기고, 명세에서 버전 고정 문구를
  제거했으며, Maven Central resolution 성공을 버전 채택 게이트로 명시했다.
  M1에서 버전 승격을 제거하고 milestone 이름을 실제 범위에 맞게 바꿨다.
  잔여 완화: 외부 문서가 말하는 버전을 그대로 믿지 않는다. 승격 전에 반드시
  `dependency:get`으로 실재 여부를 먼저 확인한다.

## Idempotence and Recovery

- 이 ExecPlan 파일 작성은 완전히 idempotent하다. 다시 실행하면 같은 파일을
  덮어쓸 뿐이다. 되돌리려면
  `rm .agent/exec-plans/ai-weekly-report-v7-2-replacement.md`.
- M0~M4는 순수 추가이며 기존 코드를 건드리지 않는다. 실패하면 새로 추가한
  파일만 지우면 된다.
- M1은 `backend/pom.xml`을 변경하지 않는다. 되돌릴 것은 새로 추가한
  `infrastructure/openai/` 파일과 제거한 `OpenAiReportConfiguration`뿐이다.
- M5의 V34는 `CREATE TABLE`만 한다. 롤백은 `DROP TABLE
  ai_weekly_report_revision` + `DELETE FROM flyway_schema_history WHERE version='34'`.
  기존 데이터 손실 없음. 부분 실패 시 Flyway가 해당 버전을 실패로 기록하므로
  `flyway repair` 후 재실행한다.
- M8~M11은 파괴적이다(엔드포인트·화면 제거). 진행 전에 milestone 단위로
  커밋해 두고, 실패 시 `git revert`로 되돌린다.
- 어떤 단계에서도 `docs/spec/AiWeeklyReport.md`와 `docs/contracts/*.schema.json`
  을 수정하지 않는다. 수정이 필요하다고 판단되면 그것은 계획 변경이므로
  구현을 멈추고 승인을 받는다(Issue #2 `Implementation boundary`).


## Interfaces and Dependencies

기존에 이미 있고 그대로 쓰는 것:

- `org.springframework.boot:spring-boot-starter-parent:3.3.5` (Jackson 2.17.2 관리)
- `com.openai:openai-java` — `4.45.0`. 버전 정본은 `backend/pom.xml`이며
  Maven Central resolution이 성공하는 버전만 채택한다
- `io.github.openhtmltopdf:openhtmltopdf-pdfbox:1.1.8` — PDF 렌더링. D7 확정으로 유지
- `org.flywaydb:flyway-core` + `flyway-mysql` — migration, 현재 head V33
- `com.teamproject.group.application.GroupAuthorization.requireActiveMember` —
  그룹 활성 멤버 확인. v7-2 §14의 authorization 요구를 충족한다.
- `com.teamproject.common.exception.ApplicationException(code, HttpStatus, message)` —
  전 도메인 공통 오류 계약
- `java.time.Clock` Bean — 테스트에서 시간 고정
- `org.testcontainers:testcontainers-mysql` — Flyway/통합 테스트
- `frontend/src/api/client.ts`의 `request` / `requestBlob` / `saveBlob`

새로 필요한 것:

- `com.networknt:json-schema-validator` (test scope) — M0의 fixture ↔ Schema
  검증용. 현재 저장소에 JSON Schema 검증 라이브러리가 없다. 버전은 Spring Boot
  BOM이 관리하지 않으므로 명시해야 한다.
- (선택) ArchUnit — `application`/`domain`에서 `com.openai.*` import 금지를
  강제. 없으면 M1에서 소스 파일 grep 기반 정적 테스트로 대체한다.
- (별도 이슈) `@playwright/test` devDependency + `test:e2e` 스크립트.

핵심 신규 시그니처:

    // application/port/AiWeeklyReportGateway.java
    public interface AiWeeklyReportGateway {
        AiWeeklyReportAnalysisV1 analyze(AiWeeklyReportSnapshotV1 snapshot);
    }

    // application/AiWeeklyReportSnapshotAssembler.java
    AiWeeklyReportSnapshotV1 assemble(Long groupId, LocalDate from,
            LocalDate toExclusive, String language);

    // application/AiWeeklyReportPolicyEngine.java
    List<RiskCandidate> candidates(AiWeeklyReportSnapshotV1 snapshot);

    // application/AiWeeklyReportAnalysisValidator.java
    ValidationResult validate(AiWeeklyReportSnapshotV1 snapshot,
            AiWeeklyReportAnalysisV1 analysis);

    // application/AiWeeklyReportFallbackFactory.java
    AiWeeklyReportAnalysisV1 create(AiWeeklyReportSnapshotV1 snapshot);

`application`과 `domain`은 `com.openai.*`를 import하지 않는다. SDK 타입은
`infrastructure.openai` 하위에서만 쓴다.


## 기존 실패 baseline (M0 이전부터 존재)

전체 백엔드 스위트는 현재 녹색이 아니다. M0 적용 **전후로 실패 집합이 동일**
하므로 M0의 회귀 delta는 0이다.

    Tests run: 176, Failures: 1, Errors: 5, Skipped: 0

재현 근거: 커밋 `f455634`만 체크아웃한 별도 git worktree
(`git worktree add <tmp> HEAD`)에서 두 테스트 클래스를 실행해 동일한 실패를
확인했다. 즉 이 6건은 M0이 만든 것이 아니다.

### AuthSecurityApiTest 1건 — 기존 결함, v7-2 무관

    AuthSecurityApiTest.publicDemoIssuesAReadOnlySession:149
      Status expected:<403> but was:<400>

M0도 M1도 막지 않는다. 별도 Issue로 분리한다.
제목: `fix(auth): restore expected read-only demo session response`

### OpenAiResponsesNarrativeAdapterTest 5건 — M1 차단 요인

    sendsOfficialSdkStatelessStructuredRequestAndParsesNarrative
    mapsRefusalAndMalformedStructuredOutputToSafeErrors
    mapsRateLimitAndProviderFailuresWithoutRetrying
    mapsTimeoutToGatewayTimeout
    rejectsCallsWhenOpenAiIsNotConfigured
      -> IO Unable to establish loopback connection

이 테스트들은 `com.sun.net.httpserver`로 로컬 스텁 서버를 띄운다. 현재 환경에서
그 바인딩이 실패한다(같은 환경에서 Python 소켓 바인딩은 성공하므로 JVM/샌드박스
쪽 문제로 보인다).

M0는 막지 않지만 **M1 착수 전에 반드시 해결한다.** M1은 OpenAI SDK를
올릴 때 그 승격의 회귀를 확인해 줄 유일한
테스트가 이 5건이기 때문이다. 위험 R15 참조.
별도 Issue 제목:
`test(report): remove environment-dependent loopback failures from OpenAI adapter tests`

해결 기준:

- 로컬 loopback 포트에 의존하지 않는 Fake/Stub transport를 쓰거나, 현재 실행
  환경에서 반복적으로 성공하는 테스트 서버 구조로 바꾼다
- 정상, refusal, incomplete, malformed, 429, 5xx, timeout을 모두 검증한다
- 실제 OpenAI API를 호출하지 않는다


## Artifacts and Notes

Issue #2 기준 커밋 확인:

    $ git log --oneline -1 bfca41430b32469c29ef513d388fdf096e68e751
    bfca414 docs(report): replace AI weekly report spec with v7-2 contract

    $ git show --stat bfca414
     docs/contracts/ai-weekly-report-analysis-v1.schema.json   |    1 +
     docs/contracts/ai-weekly-report-snapshot-v1.schema.json   |    1 +
     docs/spec/AiWeeklyReport.md                               | 2212 ++++++++---
     3 files changed, 1753 insertions(+), 461 deletions(-)

현재 SDK 의존성:

    $ cd backend && ./mvnw --batch-mode -o dependency:tree -Dincludes=com.openai
    [INFO] \- com.openai:openai-java:jar:4.45.0:compile
    [INFO]    \- com.openai:openai-java-client-okhttp:jar:4.45.0:compile
    [INFO]       \- com.openai:openai-java-core:jar:4.45.0:compile
    [INFO] BUILD SUCCESS

현재 Flyway head:

    backend/src/test/java/com/teamproject/migration/MySqlFlywayMigrationTest.java:26
        void migratesFreshMySqlSchemaFromV1ThroughV33()
    :34 assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("33");

Analysis Schema의 `analysisStatus` (D1 근거):

    "analysisStatus": { "enum": ["NORMAL", "PARTIAL", "NO_ACTION_REQUIRED"] }

spec §10.6의 같은 필드:

    public enum AnalysisStatus { COMPLETE, PARTIAL, NO_ACTION_REQUIRED }

현재 구현이 OpenAI에 팀원 지표를 보내지 않는 지점 (R1 근거):

    NarrativeContract.java:228-233
        MetricsSnapshot privateMetrics = new MetricsSnapshot(
                ..., List.of(), metrics.riskSignals(), metrics.evidence());
                     ^^^^^^^^^ members를 비운다

프론트엔드 E2E 스크립트 부재 (R10 근거):

    $ cat frontend/package.json | grep -A6 '"scripts"'
      "scripts": {
        "dev": "vite --port 5174",
        "dev:admin": "vite --mode admin --port 19091",
        "build": "tsc -b && vite build",
        "preview": "vite preview --port 5174",
        "preview:admin": "vite preview --mode admin --port 19091"
      },
    $ grep '@playwright' frontend/package-lock.json
    (출력 없음)


## Revision note

- 2026-07-31 (20:00+09:00) — Bundle 1(M2a+M2b+M2c) 완료 기록. Snapshot Java
  계약, 비식별 safeLabel factory, bulk evidence query, assembler를 추가하고
  M2 milestone에 결과·편차를 적었다. `signalCodes`/`allowed*Codes`/
  `riskCandidates`는 policy engine 범위라 빈 배열로 두고 M3로 이월했다.
  업무-일정 연결과 팀원 upcoming 일정은 도메인 관계가 없어 시간 겹침·소유자
  기준으로 파생했고, 재검토 대상으로 남겼다.

- 2026-07-31 (18:30+09:00) — M1 완료 기록. `OpenAiReportProperties`와
  `OpenAIConfiguration`을 `infrastructure/openai`에 추가하고 기존
  `OpenAiReportConfiguration`을 제거했다. Bean 이름 `openAiReportClient` 유지,
  timeout 45초, maxRetries 기본 1(허용 0~3, 명시적 0 존중),
  `responseValidation(true)`, API 키 없이 context 기동.
  `base-url`은 기존 지원을 유지했다. `max-output-tokens`와 `prompt-version`은
  소비자가 없어 M7로 미뤘다. M1 milestone에 결과와 편차를 기록했다.

- 2026-07-31 (17:30+09:00) — SDK 버전 정본 정정과 M1 재정의.
  `dependency:get -Dartifact=com.openai:openai-java:4.47.0`이 Maven Central에서
  실패해(아티팩트 부재) 지시대로 **아무 파일도 수정하지 않고 중단**했다.
  Central 메타데이터의 `latest`/`release`는 모두 `4.45.0`(2026-07-23)이다.
  이에 따라 SDK 버전 정본을 `docs/spec/AiWeeklyReport.md`가 아니라
  `backend/pom.xml`로 옮기고, Maven Central resolution 성공을 버전 채택
  게이트로 명시했다. M1에서 버전 승격을 제거하고 milestone 이름을
  `OpenAI SDK 클라이언트 설정 격리와 실행 정책 고정`으로 바꿨다.
  영향 분석 절은 승격 재검토용으로 남기되 제목과 도입부에 보류 상태를 명시했다.
  위험 R15(adapter 테스트가 M1 차단)를 해소로 기록하고, R16(계획이 실재하지
  않는 버전을 완료 조건으로 고정)을 신설·해소로 기록했다.

- 2026-07-31 (16:00+09:00) — M0 완료 및 작업 트리 판정 결과 반영.
  `Progress`에 push·트리 정리·M0 완료를 기록하고, 다음 게이트가 M1이 아니라
  OpenAI adapter 테스트 복구임을 명시했다. M0 milestone에 8개 테스트 목록,
  mutation 검증 결과, 회귀 delta 0을 추가했다. 위험 R9를 "세 선택지 중 하나"에서
  **파일별 실제 처리 결과**로 정정했다(전부 stash와 전부 커밋 모두 오답이었던
  근거 포함). 위험 R15(OpenAI adapter 테스트가 M1을 차단)를 신설했다.
  `기존 실패 baseline` 절을 신설해 176 tests / 6 failures와 clean worktree
  재현 근거, 두 건의 분리 Issue 기준을 기록했다. `Decision Log`에 파일별 격리·
  Java 25 기각·M1 선행 조건 세 건을 추가하고 `Outcomes & Retrospective`를
  갱신했다.

- 2026-07-31 (15:00+09:00) — D1~D7 제품 결정 확정 반영. `Open Product
  Decisions`를 `Confirmed Product Decisions`로 교체했다. 확정 결과에 따라
  다음을 수정했다: D6(설정 키 유지)으로 M1과 legacy 제거 목록과 SDK 분석
  7항을, D7(실제 PDF 유지)으로 API 대응표·M8·M9·Interfaces를,
  D4(초안 편집 제거)로 legacy 목록 문구를, D5(신규 테이블 + 410)로 M8을,
  D2(safeLabel 비식별 규칙)로 위험 R1과 M9를 갱신했다. 위험 R6·R8·R13은
  해소 또는 완화로 내렸고, 작업 트리 격리를 M1 선행 조건으로 R9에 승격했으며,
  milestone 단위 실행 규칙을 R14로 추가했다. `Validation and Acceptance`에
  명세 커밋과 ExecPlan 커밋을 분리하는 절차를 넣었다.
  같은 날 `docs/spec/AiWeeklyReport.md`를 교정했다(225 insertions,
  70 deletions). JSON Schema 두 개는 변경하지 않았다.

- 2026-07-31 (13:30+09:00) — 최초 작성. Issue #2, 기준 커밋 `bfca414`의 spec + Schema 2개,
  기존 백엔드 24개 main / 9개 test 파일, 프론트엔드 10개 파일, migration
  V30~V33, Maven 의존성 트리, Context7 `/openai/openai-java` 문서를 근거로
  대응표 4종·milestone 11개·legacy 제거 목록·위험 13건·Done criteria 대응표를
  작성했다. 확정되지 않은 제품 결정 7건(D1~D7)을 구현 착수의 선행 조건으로
  명시했다. 구현 코드·명세·Schema는 변경하지 않았다.
</content>
</invoke>
