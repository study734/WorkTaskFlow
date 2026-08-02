# WorkTaskFlow AI 주간 리포트 v7-2
## 실제 JSON 계약 및 구현 명세

- 상태: 구현 기준안
- 보고서 뼈대: v7 FROZEN
- 사용자 표현: v7-2
- 입력 계약: `ai-weekly-report-snapshot.v1`
- OpenAI 출력 계약: `ai-weekly-report-analysis.v1`
- 프롬프트 버전: `v7-2-prompt-001`
- OpenAI 연동: 공식 `com.openai:openai-java`. **버전 정본은 `backend/pom.xml`의 `<openai-java.version>`이다.** 이 문서는 버전을 고정하지 않는다.
- OpenAI API: Responses API + Java 타입 기반 Structured Outputs
- Spring 연동: `OpenAIClient` Bean 직접 등록. EOL된 Spring Boot Starter 사용 금지
- 명세 수정일: 2026-07-31 (D1~D7 제품 결정 확정 반영)
- 핵심 원칙: 서버가 사실·수치·날짜·후보를 확정하고, AI는 중요도·연결·설명·권고만 수행한다.
- 계약 정본: **필드 이름과 enum 값은 버전 고정 JSON Schema가 정본이다.** Java 계약 클래스는 그 Schema를 Java 타입으로 옮긴 런타임 표현이며, 둘이 어긋나면 Java 계약 클래스를 고친다. 배열 상한·참조 존재·부분집합·우선순위 연속성 같은 의미 규칙은 서버 Validator가 담당한다.
- JSON Schema 변경 금지: `docs/contracts/ai-weekly-report-snapshot-v1.schema.json`과 `docs/contracts/ai-weekly-report-analysis-v1.schema.json`은 이 교체 작업에서 변경하지 않는다.
- 본 문서의 JSON 블록과 Java 코드 블록은 **설명용 예시**다. 예시와 JSON Schema가 다르면 Schema를 따른다.

---

## 수정 이력 — 공식 OpenAI Java SDK 반영

- `Spring RestClient` 직접 호출 권고 제거
- 공식 `com.openai:openai-java` 채택 (버전은 `backend/pom.xml`이 정본)
- Responses API + Java 타입 기반 Structured Outputs로 변경
- Spring Boot 2.7 전용 EOL Starter 사용 금지 명시
- `OpenAIClient` singleton Bean과 timeout/retry 정책 추가
- SDK 전용 contract·adapter를 infrastructure 계층으로 격리
- JSON Schema와 Java 계약 드리프트 테스트 추가
- Fake Gateway 기반 통합 테스트로 변경
- Agent 실행 지침에 GitHub MCP, Context7 MCP, Maven CLI 추가

---

## 수정 이력 — D1~D7 제품 결정 확정 (2026-07-31)

교체 작업 착수 전에 명세와 JSON Schema가 어긋나던 지점, 그리고 기존 구현과
충돌하던 지점을 제품 결정으로 확정했다. 이후 구현은 이 결정을 따른다.

- **D1 분석 상태**: `analysisStatus`는 JSON Schema 기준
  `NORMAL / PARTIAL / NO_ACTION_REQUIRED`를 사용한다. 이전 초안의 `COMPLETE`는
  폐기한다.
- **D2 성과**: `achievement`는 **필수 객체**다. 성과가 없으면 `status = NONE`
  으로 표현한다. `Optional<Achievement>`를 쓰지 않는다.
- **D2 개인정보**: `safeLabel`에 **원본 업무 제목·일정 제목을 넣지 않는다.**
  서버가 생성한 비식별 의미 라벨만 전송한다. 실제 제목은 분석이 끝난 뒤
  서버가 `taskRef` / `eventRef`로 다시 결합해 표시한다.
- **D3 기간** (2026-07-31 개정): 그룹 시간대 기준으로 **이미 완료된**
  `[from, toExclusive)` 기간을 허용한다. 시작 요일과 길이는 제한하지 않으며,
  달 기준 주차·월말 절단 주차·월간·연간을 모두 받는다. 자세한 규칙은 §4.3.
- **D4 lifecycle**: 초안 편집과 수동 확정을 제거한다. 생성 성공 또는 서버
  fallback 성공 시 즉시 `FINALIZED`다. 재생성은 유지한다.
- **D5 저장소**: 새 테이블 `ai_weekly_report_revision`을 `V34` migration으로
  추가한다. 기존 `reports` 테이블의 데이터를 변환하지 않는다. 기존 AI 리포트
  ID로 접근하면 `410 AI_REPORT_LEGACY_REVISION`을 반환한다.
- **D6 설정**: 설정 키와 환경변수 이름은 **기존 `app.ai-report.*`와 기존
  환경변수를 그대로 유지한다.** 이번 교체에서 바꾸는 것은 SDK 버전과 실행
  정책(timeout 45초, maxRetries 1, `store(false)`)뿐이다.
- **D7 다운로드 (2026-08-01 개정)**: **서버가 인쇄용 HTML을 내려주고 PDF 저장은
  브라우저가 한다.** 정본 endpoint는
  `GET /api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/download`이고
  `Content-Type: text/html`이다. 기본 리포트가 이미 쓰는 방식과 같다.

  > 개정 사유: 서버 PDF 렌더러(openhtmltopdf)가 CSS 2.1까지만 이해해 flex와
  > grid를 그리지 못한다. 목표 디자인은 거의 모든 구역이 grid라 그대로 옮길 수
  > 없었다. 기존 `/pdf` endpoint는 회귀 테스트가 잡고 있어 남겨 두지만,
  > 생성 응답과 조회 뷰의 `downloadUrl`은 `/download`를 가리킨다.

---

## 수정 이력 — SDK 버전 정본 정정 (2026-07-31)

초안은 `com.openai:openai-java:4.47.0`을 필수로 요구했다. 그러나 2026-07-31
확인 시점에 Maven Central에서 해당 아티팩트가 해석되지 않는다.

    cd backend
    ./mvnw --batch-mode dependency:get -Dartifact=com.openai:openai-java:4.47.0
    [ERROR] Could not find artifact com.openai:openai-java:jar:4.47.0 in central

Maven Central 메타데이터 기준 `com.openai:openai-java`의 `latest`와 `release`는
모두 `4.45.0`(2026-07-23 배포)이며 `4.46.x`와 `4.47.0`은 배포되어 있지 않다.
공식 GitHub에는 `v4.47.0` 릴리스가 있으나 Maven 아티팩트가 없으므로 프로젝트에서
재현 가능한 버전이 아니다.

정정 내용:

- 이 문서는 SDK 버전을 고정하지 않는다. **버전 정본은 `backend/pom.xml`의
  `<openai-java.version>`이다.**
- v7-2 빌드 기준 버전은 `4.45.0`이다.
- 버전 채택 게이트를 명시한다. **Maven Central dependency resolution이 성공하는
  버전만 채택한다.** 실패하면 어떤 파일도 수정하지 않고 중단한다.
- 완료 조건과 Agent 지침의 `4.47.0` 고정 문구를 제거한다.

# 1. 최종 구현 결정

v7-2는 다음 파이프라인으로 구현한다.

```text
현재 그룹 대시보드 데이터
+ 이전 동일 기간 데이터
+ 업무별 구조화 근거
+ 확정 캘린더 일정
        ↓
AiWeeklyReportSnapshotV1
        ↓
서버 규칙 기반 위험 후보 생성
        ↓
공식 OpenAI Java SDK
Responses API
Java 타입 기반 Structured Outputs
        ↓
AiWeeklyReportAnalysisContract
        ↓
SDK 구조 역직렬화·응답 상태 확인
+ 업무 규칙 검증
+ 숫자·날짜·참조 검증
        ↓
정상: OpenAI 분석 저장
실패: 서버 Fallback 분석 저장
        ↓
v7-2 HTML 렌더링
        ↓
미리보기 / 다운로드
```

OpenAI 응답을 바로 HTML에 삽입하지 않는다. 공식 SDK가 생성한 구조화 객체도 서버의 `AiWeeklyReportAnalysisValidator`를 통과한 경우에만 도메인 분석 객체로 변환하고 렌더링한다.

## SDK 선택

- 사용: `com.openai:openai-java` (버전은 `backend/pom.xml` 정본, v7-2 빌드 기준 `4.45.0`)
- 사용 API: Responses API
- 사용 출력 방식: `ResponseCreateParams.Builder.text(AiWeeklyReportAnalysisContract.class)`
- 사용 클라이언트: `OpenAIOkHttpClient`
- 사용하지 않음: 직접 `RestClient` 호출
- 사용하지 않음: `openai-java-spring-boot-starter`
- 이유: WorkTaskFlow는 Spring Boot 3.3.5이며 공식 Starter는 Spring Boot 2.7 전용 EOL 상태다.
- 클라이언트는 애플리케이션당 하나의 singleton Bean으로 공유한다.

## 검증 계층

```text
SDK Java Schema/역직렬화
→ 응답 완료·refusal·incomplete 검사
→ JSON 계약 회귀 검사
→ WorkTaskFlow business validation
→ 도메인 객체 변환
```

Structured Outputs는 형식을 보장하는 계층이며, 실제 업무 존재 여부·허용 코드·우선순위·날짜 근거는 서버 Validator가 별도로 보장한다.

# 2. v7-2 사용자 출력 계약

## 2.1 페이지 구성

| 페이지 | 역할 | 생성 주체 |
|---|---|---|
| 1 | 확정 업무 현황 | 서버 |
| 2 | 기간 핵심 (문서에는 "이번 주·이번 달·올해·이번 기간"으로 표기) | 서버 + AI |
| 3 | 조치가 필요한 업무 | 서버 + AI |
| 4 | 결정과 실행 | 서버 + AI |

페이지 수는 A4 4페이지를 기본 계약으로 한다.

## 2.2 페이지 1 — 확정 업무 현황

기존 기본 리포트의 스타일과 서버 수치를 유지한다.

필수 항목:

- 기간 업무 수
- 완료율
- 기한 준수율
- 지연 업무 수
- 업무 완료율 progress
- 기한 준수율 progress
- 규칙 기반 기간 요약
- 업무명·상태·담당자·마감일

이 페이지에는 OpenAI 결과를 사용하지 않는다.

## 2.3 페이지 2 — 기간 핵심

필수 항목:

- AI 핵심 판단 정확히 1개
- 지난 기간 비교가 있는 경우 변화 요약
- 요청→승인→담당→진행→완료 흐름
- 성과 최대 1개
- 핵심 위험 최대 3개
- 다음 주 확정 임박 일정 최대 3개

인과관계가 확정되지 않으면 다음 표현을 사용한다.

- `가장 큰 운영 병목`
- `연관 신호`
- `우선 확인 대상`

다음 표현은 서버 근거 없이는 금지한다.

- `원인이다`
- `이 때문에 발생했다`
- `반드시 ~가 문제다`

## 2.4 페이지 3 — 조치가 필요한 업무

위험 후보를 기능별이 아닌 업무 문제별로 통합한다.

업무 카드 필드:

- 업무 표시 이름
- 현재 상태
- 담당 상태
- 마감 상태
- 체크리스트 진행
- 댓글·멘션·자료 신호
- 캘린더·일정 신호
- AI 통합 판단
- 근거 ref
- 추가 확인 필요 사항

정상 업무와 정상 팀원은 표시하지 않는다.

## 2.5 페이지 4 — 결정과 실행

결정은 최대 3개다.

각 결정 카드:

- 우선순위
- 결정 질문
- AI 권고안
- 결정권자 역할
- 실행 주체 역할
- 적용 업무 ref
- 기한의 출처
- 실행 단계
- 완료 조건

날짜·시간은 다음 중 하나에서만 가져온다.

- 기존 업무 마감
- 확정 캘린더 일정
- 팀 정책
- 회의 종료 시점
- 팀장의 수동 결정

AI가 구체적인 날짜·시각을 새로 작성하지 않는다.

# 3. 현재 저장소와 연결

현재 기본 리포트는 다음 경계를 가진다.

```text
ReportController
→ ReportDocumentService.generate()
→ DashboardService.group()
→ GroupDashboardResponse
→ 서버 HTML 렌더링
```

현재 `GroupDashboardResponse`에서 재사용 가능한 데이터:

- groupId, groupName, timezone
- periodFrom, periodTo
- totalCount
- StatusCounts
- workflowProgressPercent
- periodCreatedCount
- periodCompletedCount
- periodCompletionRatePercent
- onTimeRatePercent
- averageCompletionHours
- members
- riskTasks
- periodTasks
- calendarItems

추가 projection이 필요한 데이터:

- 체크리스트 완료/전체 수
- 댓글 개수
- 미해결 멘션 수
- 관련 자료 수
- 마지막 상태 전환
- 보류 category
- 재오픈 횟수
- 이전 동일 기간 snapshot

업무별 개별 조회를 반복하지 않고 task ID 목록 기반 bulk query로 조회한다.

# 4. 입력 계약

## 4.1 Schema 식별자

```text
ai-weekly-report-snapshot.v1
```

정식 JSON Schema:

```text
docs/contracts/ai-weekly-report-snapshot-v1.schema.json
```

## 4.2 최상위 구조

```json
{
  "schemaVersion": "ai-weekly-report-snapshot.v1",
  "reportContext": {},
  "metrics": {},
  "comparison": {},
  "workflow": {},
  "members": [],
  "tasks": [],
  "calendarConstraints": [],
  "riskCandidates": []
}
```

`generatedAt`·`language`는 `reportContext` 안에 있다. 별도 `policy` 객체는 없다 —
허용 조치 목록은 업무별 `allowed*Codes`와 위험 후보의 `allowed*Codes`로 전달한다.

## 4.3 그룹·기간

```json
{
  "reportContext": {
    "groupRef": "GROUP-7",
    "period": {
      "from": "2026-07-20",
      "toExclusive": "2026-07-27",
      "timezone": "Asia/Seoul"
    },
    "generatedAt": "2026-07-27T00:05:00Z",
    "language": "KO",
    "promptVersion": "v7-2-prompt-001"
  }
}
```

그룹 type은 전달하지 않는다. TEAM·유료 여부는 서버가 접근 제어에서 판정한다(§15).
기간 길이(`durationDays`)도 전달하지 않는다. `from`·`toExclusive`로 충분하다.

규칙 (D3 확정, 2026-07-31 개정):

- 기간은 `[from, toExclusive)`
- `from < toExclusive`이면 되고, 시작 요일과 길이는 제한하지 않는다
- 그룹 timezone 기준으로 **이미 완료된** 기간만 허용한다
  (`toExclusive`가 오늘보다 뒤면 거부)
- 달 기준 주차(매월 1·8·15·22·29일 시작), 월말 절단 주차, 월간·연간 기간을
  모두 허용한다
- 직전 비교 기간은 선택 기간과 **같은 길이**로 바로 앞에 붙인다
- 날짜 계산은 그룹 timezone 기준
- OpenAI가 기간을 재해석하지 않음

> 개정 사유: 대시보드의 기간 선택(주차·월간·연간)이 달 기준 날짜 묶음이라
> 월요일 7일 제약과 구조적으로 맞지 않았다. 화면에서 고른 기간을 그대로
> 생성할 수 있게 서버 제약을 기간 유효성과 완료 여부로만 좁혔다.
> JSON Schema와 migration은 변경하지 않았다.

## 4.4 Metrics

```json
{
  "metrics": {
    "periodTaskCount": 12,
    "completionRatePercent": 42,
    "onTimeRatePercent": 75,
    "delayedCount": 2,
    "averageCompletionHours": 34
  }
}
```

상태별 건수는 `workflow`(§4.6)에 있다. `metrics`에는 `statusCounts`가 없다.

이 값들은 서버 계산값이다.

OpenAI는 다음을 할 수 없다.

- 재계산
- 반올림 변경
- 새 KPI 추가
- 숫자 수정

## 4.5 Comparison

비교 데이터가 있는 경우:

```json
{
  "comparison": {
    "status": "AVAILABLE",
    "previousFrom": "2026-07-13",
    "previousToExclusive": "2026-07-20",
    "periodTaskCountDelta": 2,
    "completionRatePointDelta": -8,
    "onTimeRatePointDelta": -8,
    "delayedCountDelta": 1
  }
}
```

중첩 객체가 아니라 평평한 필드다.

없는 경우:

```json
{
  "comparison": {
    "status": "NO_BASELINE"
  }
}
```

`NO_BASELINE`이면 AI 출력에서 다음 표현을 금지한다.

- 증가·감소
- 개선·악화
- 전주 대비
- 추세

## 4.6 Workflow

```json
{
  "workflow": {
    "requested": 2,
    "acceptedUnassigned": 1,
    "assignedNotStarted": 1,
    "inProgress": 2,
    "onHold": 1,
    "completed": 5
  }
}
```

서버가 업무 이력과 현재 상태를 기반으로 계산한다.

## 4.7 Member summary

OpenAI에는 표시 이름 대신 ref를 전달한다.

```json
{
  "memberRef": "MEMBER-3",
  "role": "MEMBER",
  "assignedCount": 4,
  "activeCount": 3,
  "completedCount": 1,
  "delayedCount": 1,
  "onTimeRatePercent": 25,
  "upcomingCalendarCount": 2
}
```

팀원 추천은 `activeCount`·`delayedCount`·`upcomingCalendarCount`가 있을 때만 허용한다.
(`dueSoonCount`·`calendarConflictCount`는 계약에 없다. 마감 임박은 업무 단위 `dueState`,
일정 충돌은 `calendarConstraints`로 판단한다.)

AI는 개인의 능력·성실성·태도를 평가하지 않는다.

## 4.8 Task evidence

```json
{
  "taskRef": "TASK-104",
  "safeLabel": "승인 후 담당자가 없는 지연 업무",
  "priority": "HIGH",
  "status": "TODO",
  "assigneeRef": null,
  "dueAt": "2026-07-25T18:00:00+09:00",
  "dueState": "OVERDUE",
  "createdAt": "2026-07-21T10:00:00+09:00",
  "checklist": {
    "completed": 0,
    "total": 5
  },
  "collaboration": {
    "commentCount": 2,
    "unresolvedMentionCount": 1,
    "resourceLinkCount": 0
  },
  "history": {
    "lastTransitionCode": "REQUESTED_TO_TODO",
    "holdReasonCategory": "NONE",
    "reopenedCount": 0
  },
  "calendarEventRefs": ["EVENT-14"],
  "signalCodes": [
    "APPROVED_UNASSIGNED",
    "OVERDUE",
    "CHECKLIST_NOT_STARTED",
    "RESOURCE_MISSING"
  ],
  "allowedDecisionOptionCodes": ["ASSIGN_OWNER_AND_SET_DUE"],
  "allowedExecutionStepCodes": ["ASSIGN_OWNER", "SET_DUE"],
  "allowedCompletionSignalCodes": ["ASSIGNEE_SET", "DUE_AT_SET"]
}
```

보류 사유는 `history.holdReasonCategory`의 구조화 값이다. 별도 `blocker` 객체는 없고
자유 입력 사유는 어떤 형태로도 실리지 않는다. `category`(업무 분류)도 계약에 없다.

### 개인정보 경계

전송 금지:

- 실제 이름
- **업무 제목 원문**
- **캘린더 일정 제목 원문**
- 댓글 원문
- 업무 description 원문
- 첨부파일 본문
- 자유 입력 보류 사유 원문
- 이메일·전화번호
- 사용자 ID

허용:

- 익명 ref
- **서버가 생성한 비식별 의미 라벨(safeLabel)**
- 구조화 category
- 상태·수치·날짜
- 집계된 협업 신호

### safeLabel 규칙 (D2 확정)

`safeLabel`은 업무나 일정을 **식별**하는 문자열이 아니라, 서버가 상태·담당·
마감·체크리스트·보류 신호에서 조합한 **의미 유형 문장**이다. 원본 제목의
어떤 조각도 포함하지 않는다.

허용 예:

```text
승인 후 담당자가 없는 지연 업무
외부 의존성으로 보류 중인 발표 준비 업무
체크리스트가 시작되지 않은 임박 업무
팀 전체가 참여하는 확정 회의
```

금지 예:

```text
사용자 테스트 결과 반영
고객사 A 계약서 수정
김민준 발표 자료 최종 검토
전체 리허설
```

실제 제목과 실제 이름은 OpenAI 분석이 끝난 뒤 서버 renderer가 `taskRef`·
`eventRef`·`memberRef`로 다시 결합해 사용자 화면과 PDF에만 표시한다(§8.1).
이 규칙 덕분에 AI는 업무의 의미 유형을 파악하면서도 원문 개인정보 경계는
기존 구현과 동일하게 유지된다.

## 4.9 Calendar event

```json
{
  "eventRef": "EVENT-14",
  "type": "MEETING",
  "safeLabel": "팀 전체가 참여하는 확정 회의",
  "startAt": "2026-07-31T14:00:00+09:00",
  "endAt": "2026-07-31T16:00:00+09:00",
  "relatedTaskRefs": ["TASK-104"]
}
```

일정 소유자(`ownerRef`)는 계약에 없다. 팀원별 다가오는 일정 수는
`members[].upcomingCalendarCount`로 집계해 전달한다.

OpenAI는 `eventRef`를 선택할 수만 있다.

표시 날짜는 서버 renderer가 eventRef를 다시 조회하여 삽입한다.

# 5. 서버 위험 후보 계약

OpenAI가 전체 업무에서 자유롭게 위험을 생성하지 않는다.

서버가 먼저 위험 후보를 만든다.

## 5.1 Candidate 구조

```json
{
  "candidateRef": "RISK-001",
  "code": "APPROVED_UNASSIGNED_OVERDUE",
  "severity": "HIGH",
  "taskRefs": ["TASK-104"],
  "memberRefs": [],
  "eventRefs": ["EVENT-14"],
  "evidenceCodes": [
    "APPROVED_UNASSIGNED",
    "OVERDUE",
    "CHECKLIST_NOT_STARTED"
  ],
  "allowedDecisionOptions": [
    "ASSIGN_OWNER",
    "REPLAN_SCOPE"
  ],
  "allowedExecutionSteps": [
    "ASSIGN_TASK_OWNER",
    "RESET_TASK_DUE",
    "START_FIRST_CHECKLIST_ITEM"
  ],
  "allowedCompletionSignals": [
    "ASSIGNEE_PRESENT",
    "DUE_DATE_PRESENT",
    "CHECKLIST_PROGRESS_GT_ZERO"
  ],
  "missingEvidence": []
}
```

## 5.2 MVP 위험 코드

| 코드 | 조건 |
|---|---|
| `APPROVAL_PENDING` | REQUESTED 상태 장기화 |
| `APPROVED_UNASSIGNED` | TODO 상태 + 담당자 없음 |
| `APPROVED_UNASSIGNED_OVERDUE` | 미할당 + 마감 초과 |
| `OVERDUE_ACTIVE` | 미완료 + 마감 초과 |
| `ON_HOLD_LONG` | ON_HOLD 장기화 |
| `CHECKLIST_NOT_STARTED` | checklist total > 0, completed = 0 |
| `RESOURCE_MISSING` | 관련 자료가 필요한 후보인데 연결 자료 0 |
| `UNRESOLVED_MENTION` | 미해결 멘션 존재 |
| `WORKLOAD_CONCENTRATION` | 한 팀원에게 활성·임박 업무 집중 |
| `SCHEDULE_CONFLICT` | 업무 마감과 캘린더 일정 충돌 |
| `COMPLETION_RATE_DROP` | baseline 존재 + 완료율 유의미 하락 |
| `BACKLOG_GROWTH` | baseline 존재 + 미완료 업무 증가 |

`RESOURCE_MISSING`처럼 업무 의미를 요구하는 규칙은 task category 또는 명시적 requirement가 있을 때만 생성한다.

# 6. OpenAI 출력 계약

## 런타임 정본

OpenAI 출력의 런타임 구조 정본은 다음 Java 계약 클래스다.

```text
AiWeeklyReportAnalysisContract
├─ schemaVersion
├─ analysisStatus
├─ executiveJudgment
├─ achievement
├─ issues[]
└─ globalMissingEvidence[]
```

공식 SDK가 이 클래스에서 JSON Schema를 생성하고, 응답을 동일 클래스 인스턴스로 역직렬화한다.

기존 `ai-weekly-report-analysis-v1.schema.json`은 다음 용도로 유지한다.

- 프론트·문서·외부 계약 공유
- fixture 검증
- SDK 버전 변경 시 계약 드리프트 탐지
- 서버 Fallback JSON 검증

빌드 테스트에서 Java 계약 클래스의 필드·enum과 저장된 JSON Schema의 공개 계약이 어긋나면 실패시킨다. 배열 최대 3개, ref 존재 여부, 허용 코드 부분집합 같은 의미 제약은 `AiWeeklyReportAnalysisValidator`가 담당한다.

## 6.1 Schema 식별자

```text
ai-weekly-report-analysis.v1
```

정식 Schema:

```text
docs/contracts/ai-weekly-report-analysis-v1.schema.json
```

## 6.2 출력 예시

```json
{
  "schemaVersion": "ai-weekly-report-analysis.v1",
  "analysisStatus": "NORMAL",
  "executiveJudgment": {
    "headline": "승인 후 담당 연결과 보류 해제가 이번 주 우선 병목입니다.",
    "interpretation": "확정 데이터에서 미할당 지연 업무와 장기 보류 업무가 동시에 확인됩니다.",
    "metricRefs": [
      "metrics.completionRatePercent",
      "metrics.delayedTaskCount"
    ],
    "evidenceTaskRefs": ["TASK-104", "TASK-110"],
    "confidence": "HIGH",
    "missingEvidence": []
  },
  "achievement": {
    "status": "AVAILABLE",
    "headline": "핵심 화면 구현 완료",
    "summary": "완료 상태와 체크리스트 완료가 모두 확인된 업무입니다.",
    "evidenceTaskRefs": ["TASK-101"]
  },
  "issues": [
    {
      "priority": "P1",
      "candidateRef": "RISK-001",
      "severity": "HIGH",
      "title": "승인 후 미할당 업무",
      "impact": "테스트 결과 반영 착수가 지연될 수 있습니다.",
      "confidence": "HIGH",
      "taskRefs": ["TASK-104"],
      "evidenceCodes": [
        "APPROVED_UNASSIGNED",
        "OVERDUE",
        "CHECKLIST_NOT_STARTED"
      ],
      "missingEvidence": [],
      "integratedJudgment": "담당자 지정과 마감 재조정이 같은 회의에서 필요합니다.",
      "requiredDecision": "담당자를 어떤 기준으로 지정할지 결정합니다.",
      "decision": {
        "title": "검증 업무 담당자 지정",
        "question": "이 업무를 누가 담당할 것인가?",
        "recommendedOptionCode": "ASSIGN_OWNER",
        "recommendation": "팀원별 활성 업무와 임박 일정을 확인하여 담당자를 지정합니다.",
        "decisionMakerRole": "LEADER",
        "actionOwnerRole": "ASSIGNEE_TO_BE_SELECTED",
        "deadline": {
          "source": "LEADER_DECISION_REQUIRED",
          "referenceRef": null
        },
        "executionStepCodes": [
          "ASSIGN_TASK_OWNER",
          "RESET_TASK_DUE",
          "START_FIRST_CHECKLIST_ITEM"
        ],
        "completionSignalCodes": [
          "ASSIGNEE_PRESENT",
          "DUE_DATE_PRESENT",
          "CHECKLIST_PROGRESS_GT_ZERO"
        ]
      }
    }
  ],
  "globalMissingEvidence": []
}
```

## 6.3 출력 개수 제한

- executiveJudgment: 정확히 1개
- achievement: **항상 1개 객체**. 성과가 없으면 `status = NONE`이고
  `headline`·`summary`는 빈 문자열, `evidenceTaskRefs`는 빈 배열이다 (D2)
- issues: 0~3개
- issue priority: P1부터 연속
- taskRefs: 각 항목 최소 1개
- completionSignalCodes: 결정당 최소 1개

## 6.4 Confidence

허용값:

```text
HIGH
MEDIUM
INSUFFICIENT_EVIDENCE
```

규칙:

- `INSUFFICIENT_EVIDENCE`이면 `missingEvidence` 1개 이상
- `HIGH`인데 missingEvidence가 있으면 검증 실패
- 원인 추정만 가능한 경우 최대 `MEDIUM`

# 7. 서버 의미 검증

JSON Schema 검증 이후 별도의 업무 규칙 검증을 수행한다.

## 7.1 Reference 검증

- candidateRef가 Snapshot에 존재
- 모든 taskRef가 Snapshot tasks에 존재
- eventRef가 Snapshot calendarEvents에 존재 (deadline.referenceRef 경로에서 검사)
- metricRef가 허용된 metric path

> 2026-08-02: AI 출력 계약에 memberRef를 담는 필드가 없다(§6.2). 검사 대상이 없으므로
> 목록에서 뺀다. 산문에 쓰인 `MEMBER-n`은 검증 대상이 아니라 렌더링 시 치환 대상이다(§8.1).

## 7.2 Candidate subset 검증

AI가 선택한 다음 값은 candidate 허용 목록의 부분집합이어야 한다.

- recommendedOptionCode
- executionStepCodes
- completionSignalCodes

## 7.3 상태 검증

- 성과 taskRef는 실제 `COMPLETED`
- 미할당 위험은 근거 업무 중 **적어도 하나**가 assigneeRef null
- 지연 위험은 근거 업무 중 적어도 하나가 dueState `OVERDUE`
- 보류 위험은 근거 업무 중 적어도 하나가 status `ON_HOLD`
- 체크리스트 위험은 근거 업무 중 적어도 하나가 completed 0

> 2026-08-02: "모든 근거 업무가 만족"으로 구현했더니 서버 자체 fallback이 거부됐다.
> 위험 후보 하나가 업무 여러 건과 신호 여러 개를 묶기 때문이다(부하 편중 후보에 지연 업무가
> 섞이는 식). 근거가 **하나도 없는** 주장만 걸리도록 완화했다.

## 7.4 비교 검증

`comparison.status == NO_BASELINE`이면 다음을 금지한다.

- metric delta ref
- 전주 대비 문장
- 개선·악화 판단

## 7.5 우선순위 검증

허용:

```text
[]
[P1]
[P1, P2]
[P1, P2, P3]
```

금지:

```text
[P2]
[P1, P1]
[P1, P3]
```

## 7.6 날짜 검증

AI 출력에는 임의 ISO datetime 필드를 두지 않는다.

Deadline은 다음 구조만 사용한다.

```json
{
  "source": "CALENDAR_EVENT",
  "referenceRef": "EVENT-14"
}
```

허용 source:

- `MEETING_END`
- `TASK_DUE`
- `CALENDAR_EVENT`
- `LEADER_DECISION_REQUIRED`

renderer가 서버 데이터에서 실제 날짜를 삽입한다.

# 8. 서버 렌더링 계약

OpenAI 문자열에 수치와 날짜를 직접 넣는 것을 최소화한다.

권장 방식:

```text
AI: metricRef, taskRef, eventRef, code 선택
서버: 실제 label, 숫자, 날짜, 이름 삽입
```

예:

```text
AI 출력
metricRefs = [metrics.completionRatePercent]

서버 출력
완료율은 42%이며 지난주보다 8%p 낮습니다.
```

## 8.1 표시 이름 재결합

Snapshot:

```text
MEMBER-3
TASK-104
```

렌더링:

```text
김민준
사용자 테스트 결과 반영
```

재결합은 서버에서 수행한다.

재결합 대상은 `evidenceTaskRefs` 같은 구조화 ref 필드뿐 아니라 **모델이 문장 안에
직접 써 넣은 ref까지** 포함한다. 대상 텍스트 필드는 다음과 같다.

- `executiveJudgment.headline` / `.interpretation`
- `achievement.headline` / `.summary`
- `issue.title` / `.impact` / `.integratedJudgment` / `.requiredDecision`
- `decision.title` / `.question` / `.recommendation`

프롬프트는 모델에게 ref를 그대로 쓰라고 지시한다. ref를 피하려다 근거 연결을 잃거나
제목을 지어내는 쪽이 더 나쁘다.

해석에 실패한 ref도 원시 식별자를 남기지 않는다. 문서 언어에 맞춘 비식별 라벨
(`확인할 수 없는 업무` / `Unidentified task` 등)로 바꾼다.

```text
입력  TASK-6은 URGENT 우선순위이며 아직 TODO이다.
출력  결제 실패 로그 확인은 URGENT 우선순위이며 아직 TODO이다.
```

> 개정 사유: projector가 구조화 필드만 재결합해서 사용자 문서에 `TASK-6` 같은 내부
> 식별자가 그대로 찍혔다. 8.1의 서버 책임을 텍스트 필드까지 넓힌다.

## 8.2 사용자 본문에서 숨길 정보

- Structured Outputs
- JSON Schema
- 모델 ID
- token 수
- OpenAI 호출 시간
- schema validation 결과
- retry 횟수

사용자 하단 표시:

```text
분석 상태 정상 · 모든 판단에 근거 업무가 연결되었습니다.
```

Fallback 시:

```text
기본 분석으로 생성됨 · 확정 업무 데이터 기준
```

# 9. API 계약

## 9.1 생성

```http
POST /api/v1/groups/{groupId}/reports/ai-weekly
```

Request:

```json
{
  "from": "2026-07-20",
  "toExclusive": "2026-07-27",
  "language": "KO",
  "regenerate": false
}
```

Validation:

- from 필수
- toExclusive 필수
- `from < toExclusive` (기간 길이 제약 없음 — D3 개정)
- language: KO 또는 EN
- group timezone 기준 완료된 기간
- 생성 권한 필요

> 2026-08-02 정정: 여기 있던 "기간 7일"은 D3(§4.3)이 달 기준 주차·월간·연간을 허용하도록
> 개정한 뒤에도 남아 있던 문장이다. 구현은 D3를 따른다.

Response:

```json
{
  "reportId": 91,
  "groupId": 7,
  "from": "2026-07-20",
  "toExclusive": "2026-07-27",
  "revision": 1,
  "status": "FINALIZED",
  "analysisMode": "OPENAI",
  "generatedAt": "2026-07-27T00:05:10Z",
  "downloadUrl": "/api/v1/groups/7/reports/ai-weekly/91/download",
  "createdNew": true,
  "sourceChanged": false
}
```

`createdNew`가 false면 저장된 revision을 그대로 돌려준 것이고 OpenAI를 부르지 않았다.
프런트는 이 값으로 재생성 여부를 사용자에게 묻는다.

`sourceChanged`는 저장본을 돌려줄 때 그 리포트를 만든 뒤 업무 데이터가 바뀌었는지다.
서버가 지금 snapshot의 `source_fingerprint`를 저장본의 것과 비교한다. 재생성이 유료이므로
사용자가 그 값어치를 판단할 근거로 쓴다. `createdNew`가 true면 항상 false다.

HTTP:

- 새 생성: `201 Created`
- 동일 결과 재사용: `200 OK`
- 같은 기간이 동시에 생성 중이라 번호를 확보하지 못함: `409 AI_REPORT_CONCURRENT_GENERATION`

> 2026-08-02 정정: `202 Accepted`(생성 진행 중)를 제거했다. 생성은 동기 처리이고 진행 중
> 상태를 돌려주는 경로가 없다. §14의 GENERATING revision 선저장과 한 세트이며 둘 다
> 미구현이다.

## 9.2 조회

```http
GET /api/v1/groups/{groupId}/reports/ai-weekly/{reportId}
```

저장된 revision을 반환한다.

OpenAI를 호출하지 않는다.

## 9.3 다운로드

```http
GET /api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/download
```

규칙 (D7 2026-08-01 개정):

- FINALIZED만 다운로드
- 저장 revision 사용
- OpenAI 재호출 금지
- `Cache-Control: private, no-store`
- Content-Type: `text/html`
- 파일명: `toesa-ai-weekly-{groupId}-{from}-r{revision}-{ko|en}.html`
- 문서 언어는 revision에 저장된 언어를 따른다. 요청 시점 화면 언어를 쓰면
  EN 분석에 한국어 껍데기가 씌워진다.

`GET .../{reportId}/pdf`는 회귀 테스트가 잡고 있어 남아 있지만 산출물 정본이
아니다. 생성 응답과 조회 뷰의 `downloadUrl`은 `/download`를 가리킨다.
- 기존 endpoint 경로 `/{reportId}/pdf`를 그대로 유지한다

**실제 PDF를 유지한다.** 기존 구현의 OpenHTMLtoPDF 렌더러
(`OpenHtmlReportPdfRenderer`)를 재사용하고, `renderWeeklyAi()`의 본문만 v7-2
4페이지 구조로 교체한다. `renderBasic()`(기본 리포트)은 변경하지 않는다.
HTML 다운로드로 후퇴하지 않는다.

# 10. 공식 OpenAI Java SDK 연동

## 10.1 Maven 의존성

## SDK 버전 정본과 채택 게이트

**SDK 버전의 정본은 이 문서가 아니라 `backend/pom.xml`의 `<openai-java.version>`이다.**
이 명세는 특정 버전을 필수로 요구하지 않는다.

v7-2 빌드 기준 버전은 `4.45.0`이다. 2026-07-31 기준 Maven Central의
`com.openai:openai-java` 최신 릴리스가 `4.45.0`이며, 이것이 프로젝트에서
재현 가능한 버전이다.

버전 채택 게이트: **어떤 버전이든 Maven Central에서 dependency resolution이
성공해야 채택한다.** 공식 GitHub에 릴리스 태그가 있어도 Maven Central에
아티팩트가 배포되기 전에는 `<openai-java.version>`을 올리지 않는다. 승격 전에
반드시 다음을 통과시킨다.

```bash
cd backend
./mvnw --batch-mode dependency:get -Dartifact=com.openai:openai-java:<version>
```

이 명령이 실패하면 어떤 파일도 수정하지 않고 중단한다.

참고: 공식 GitHub에는 `v4.47.0` 릴리스가 존재하지만 2026-07-31 확인 시점에
Maven Central에서는 `com.openai:openai-java:4.47.0`이 해석되지 않았다
(`Could not find artifact ... in central`). 따라서 v7-2는 `4.45.0`을 기준으로
빌드한다. Central에 배포되면 위 게이트를 통과시킨 뒤 별도 작업으로 승격한다.

```xml
<properties>
    <!-- 버전 정본. 승격은 Maven Central resolution 성공을 전제로 한다. -->
    <openai-java.version>4.45.0</openai-java.version>
</properties>

<dependencies>
    <!-- 기존 의존성 유지 -->

    <dependency>
        <groupId>com.openai</groupId>
        <artifactId>openai-java</artifactId>
        <version>${openai-java.version}</version>
    </dependency>
</dependencies>
```

금지:

```xml
<artifactId>openai-java-spring-boot-starter</artifactId>
```

공식 Starter는 Spring Boot 2.7 전용이며 EOL 상태다. WorkTaskFlow의 Spring Boot 3.3.5에서는 framework-neutral SDK를 직접 사용한다.

의존성 확인 명령:

```bash
mvn -f backend/pom.xml dependency:tree -Dincludes=com.openai:openai-java
mvn -f backend/pom.xml dependency:tree -Dincludes=com.fasterxml.jackson.core
```

SDK는 Jackson 2.13.4 이상과 호환되며 런타임에 비호환 버전을 감지한다. Spring Boot BOM과 SDK의 Jackson 의존성 충돌 여부를 테스트 환경에서 확인하고, SDK의 Jackson 호환성 검사를 임의로 끄지 않는다.

## 10.2 환경 변수와 애플리케이션 설정

D6 확정: **설정 키와 환경변수 이름을 바꾸지 않는다.** 저장소가 이미 쓰는
`app.ai-report.*` prefix와 기존 환경변수를 그대로 유지한다. 이름을 바꾸면
배포 환경(`infra/single-ec2/compose.yml`, GitHub Actions secrets, 운영 `.env`)
을 동시에 고쳐야 하고, 누락 시 운영에서 조용히 fallback으로만 동작한다.
사용자 기능에는 아무 이득이 없다.

`.env` 또는 실행 환경 (기존 이름 유지):

```env
OPENAI_API_KEY=sk-proj-...
AI_REPORT_ENABLED=false
OPENAI_MODEL=<validated-model-id>
OPENAI_REQUEST_TIMEOUT=45s
```

`backend/src/main/resources/application.properties` (기존 파일 형식 유지.
이 저장소는 `application.yml`을 쓰지 않는다):

```properties
app.ai-report.enabled=${AI_REPORT_ENABLED:false}
app.ai-report.api-key=${OPENAI_API_KEY:}
app.ai-report.model=${OPENAI_MODEL:}
app.ai-report.request-timeout=${OPENAI_REQUEST_TIMEOUT:45s}
app.ai-report.max-retries=${OPENAI_MAX_RETRIES:1}
app.ai-report.max-output-tokens=${OPENAI_MAX_OUTPUT_TOKENS:3000}
app.ai-report.prompt-version=v7-2-prompt-001
```

이번 교체에서 실제로 바뀌는 값은 다음뿐이다.

```text
request-timeout  90s → 45s
maxRetries         0 → 1
store(false)          유지
```

규칙:

- API 키는 `app.ai-report.api-key`(기본값은 `OPENAI_API_KEY` 환경변수)로 주입한다.
- API 키를 설정 파일에 하드코딩하거나 로그·DB·Snapshot에 저장하지 않는다.
- 모델 ID는 코드 상수 대신 설정으로 주입한다. 기본값을 두지 않는다.
- 발표·운영 검증 시에는 평가를 통과한 모델 snapshot을 고정한다.
- `AI_REPORT_ENABLED=false`이거나 API 키·모델이 비어 있으면 API 호출 없이
  Fallback을 사용한다.

## 10.3 설정 Properties

```java
package com.teamproject.report.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.ai-report")
public record OpenAiReportProperties(
        boolean enabled,
        String apiKey,
        String model,
        String promptVersion,
        Duration requestTimeout,
        int maxRetries,
        long maxOutputTokens
) {
    public OpenAiReportProperties {
        if (requestTimeout == null) requestTimeout = Duration.ofSeconds(45);
        if (maxRetries < 0 || maxRetries > 2) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 2");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }
}
```

## 10.4 `OpenAIClient` Bean

```java
package com.teamproject.report.infrastructure.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenAiReportProperties.class)
public class OpenAIConfiguration {

    @Bean
    OpenAIClient openAIClient(OpenAiReportProperties properties) {
        return OpenAIOkHttpClient.builder()
                .apiKey(properties.apiKey().isBlank()
                        ? "not-configured" : properties.apiKey())
                .timeout(properties.requestTimeout())
                .maxRetries(properties.maxRetries())
                .responseValidation(true)
                .build();
    }
}
```

`fromEnv()`를 사용하지 않는다. `fromEnv()`는 `OPENAI_API_KEY`가 없으면 Bean
생성 시점에 예외를 던져 Spring context 기동 자체를 막는다. §17의 SDK smoke
test는 "실제 API 키 없이 실행"을 요구하므로, 키가 비어 있어도 Bean이 만들어지고
호출 시점에 `OpenAiReportUnavailableException`으로 Fallback 전환되도록
명시적 `.apiKey(...)`를 쓴다.

SDK 기본 timeout은 10분이며 기본 재시도 횟수는 2회다. 주간 리포트 생성에서는 timeout을 45초, 재시도를 최대 1회로 제한한다. SDK가 재시도하는 연결 오류·408·409·429·5xx 외에 애플리케이션이 별도 중첩 재시도를 추가하지 않는다.

## 10.5 포트 인터페이스

```java
package com.teamproject.report.application.port;

import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;

public interface AiWeeklyReportGateway {
    AiWeeklyReportAnalysisV1 analyze(AiWeeklyReportSnapshotV1 snapshot);
}
```

Application 계층은 공식 SDK 타입을 참조하지 않는다. SDK 타입은 infrastructure adapter 내부에서만 사용한다.

## 10.6 Structured Outputs 계약 클래스

SDK Schema 생성 전용 클래스는 JPA Entity, Dashboard DTO, API 응답 DTO와 분리한다.

```java
package com.teamproject.report.infrastructure.openai.contract;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Optional;

@JsonClassDescription("Validated AI analysis for the WorkTaskFlow v7-2 weekly report")
public final class AiWeeklyReportAnalysisContract {

    public String schemaVersion;
    public AnalysisStatus analysisStatus;
    public ExecutiveJudgment executiveJudgment;
    public Achievement achievement;
    public List<Issue> issues;
    public List<String> globalMissingEvidence;

    public enum AnalysisStatus {
        NORMAL,
        PARTIAL,
        NO_ACTION_REQUIRED
    }

    public enum AchievementStatus {
        AVAILABLE,
        NONE
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        INSUFFICIENT_EVIDENCE
    }

    public enum Priority {
        P1,
        P2,
        P3
    }

    public static final class ExecutiveJudgment {
        public String headline;
        public String interpretation;
        public List<String> metricRefs;
        public List<String> evidenceTaskRefs;
        public Confidence confidence;
        public List<String> missingEvidence;
    }

    public static final class Achievement {
        public AchievementStatus status;
        public String headline;
        public String summary;
        public List<String> evidenceTaskRefs;
    }

    public static final class Issue {
        public Priority priority;
        public String candidateRef;
        public String severity;
        public String title;
        public String impact;
        public Confidence confidence;
        public List<String> taskRefs;
        public List<String> evidenceCodes;
        public List<String> missingEvidence;
        public String integratedJudgment;
        public String requiredDecision;
        public Decision decision;
    }

    public static final class Decision {
        public String title;
        public String question;
        public String recommendedOptionCode;
        public String recommendation;
        public String decisionMakerRole;
        public String actionOwnerRole;
        public Deadline deadline;
        public List<String> executionStepCodes;
        public List<String> completionSignalCodes;
    }

    public static final class Deadline {
        @JsonPropertyDescription("MEETING_END, TASK_DUE, CALENDAR_EVENT, or LEADER_DECISION_REQUIRED")
        public String source;
        public Optional<String> referenceRef;
    }
}
```

계약 규칙:

- Java `Map` 사용 금지
- 임의 key-value는 named entry list로 모델링
- 필드 이름과 enum 값은 저장 JSON Schema를 그대로 따른다
- `Optional<T>`는 **JSON Schema가 nullable로 정의한 필드에만** 쓴다
  (예: `Deadline.referenceRef`). 선택성을 status enum으로 표현한 필드
  (예: `achievement.status`)에는 쓰지 않는다 (D2)
- 모든 schema 대상 클래스는 최소 1개 이상의 공개 필드 또는 getter를 가진다.
- enum은 OpenAI 출력에서 허용할 값만 정의한다.
- 최대 배열 길이와 cross-field 규칙은 서버 Validator에서 검사한다.
- 민감한 원문이 역직렬화 오류 메시지에 포함될 수 있으므로 예외 전체 메시지를 운영 로그에 남기지 않는다.

## 10.7 SDK Adapter

```java
package com.teamproject.report.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.port.AiWeeklyReportGateway;
import com.teamproject.report.infrastructure.openai.contract.AiWeeklyReportAnalysisContract;

import java.util.Objects;

public final class OpenAiWeeklyReportGateway implements AiWeeklyReportGateway {

    private final OpenAIClient client;
    private final OpenAiReportProperties properties;
    private final OpenAiReportPrompt prompt;
    private final ObjectMapper objectMapper;
    private final OpenAiAnalysisContractMapper mapper;

    public OpenAiWeeklyReportGateway(
            OpenAIClient client,
            OpenAiReportProperties properties,
            OpenAiReportPrompt prompt,
            ObjectMapper objectMapper,
            OpenAiAnalysisContractMapper mapper
    ) {
        this.client = client;
        this.properties = properties;
        this.prompt = prompt;
        this.objectMapper = objectMapper;
        this.mapper = mapper;
    }

    @Override
    public AiWeeklyReportAnalysisV1 analyze(AiWeeklyReportSnapshotV1 snapshot) {
        if (!properties.enabled()) {
            throw new OpenAiReportUnavailableException("OpenAI report is disabled");
        }
        if (properties.model() == null || properties.model().isBlank()) {
            throw new OpenAiReportUnavailableException("app.ai-report.model is missing");
        }

        String snapshotJson = serialize(snapshot);

        StructuredResponseCreateParams<AiWeeklyReportAnalysisContract> params =
                ResponseCreateParams.builder()
                        .instructions(prompt.developerInstruction())
                        .input(snapshotJson)
                        .text(AiWeeklyReportAnalysisContract.class)
                        .model(properties.model())
                        .maxOutputTokens(properties.maxOutputTokens())
                        .store(false)
                        .build();

        var response = client.responses().create(params);

        AiWeeklyReportAnalysisContract contract = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new OpenAiReportInvalidResponseException(
                        "No structured output_text was returned"));

        return mapper.toDomain(contract);
    }

    private String serialize(AiWeeklyReportSnapshotV1 snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Snapshot serialization failed", exception);
        }
    }
}
```

주의:

- 실제 사용 중인 SDK 버전의 컴파일 타입을 IDE와 공식 예제로 확인한 뒤 import를 확정한다.
- `instructions`에는 고정 Developer Prompt만 넣는다.
- `input`에는 Snapshot JSON만 넣어 Prompt Caching 친화적인 순서를 유지한다.
- `store(false)`를 명시한다.
- 스트리밍·background mode·tool 호출은 사용하지 않는다.
- 결과 배열의 첫 번째 item을 무조건 가정하지 않고 message/outputText를 탐색한다.
- refusal, incomplete, failed 상태는 adapter 예외로 변환한다.
- `AiWeeklyReportAnalysisContract`를 즉시 application DTO로 매핑한 뒤 business validation을 수행한다.

## 10.8 응답·오류 처리

Adapter가 분류할 오류:

```text
OpenAiReportUnavailableException
OpenAiReportTimeoutException
OpenAiReportRateLimitException
OpenAiReportInvalidResponseException
OpenAiReportContractException
```

Fallback 전환 대상:

- API key 또는 model 설정 누락
- timeout
- SDK 재시도 후 연결 실패
- 408·409·429·5xx
- response status가 `incomplete` 또는 `failed`
- refusal
- Structured Output 역직렬화 실패
- 응답 content 없음
- application business validation 실패

재시도하지 않는 오류:

- 400 계열 요청 계약 오류
- Java Schema 생성 오류
- SDK local schema validation 실패
- 존재하지 않는 ref
- 허용되지 않은 option/step/completion code
- 의미 검증 실패

## 10.9 로그와 관측성

저장 가능:

- reportId
- groupId
- sourceHash
- promptVersion
- model
- SDK version
- response id
- request duration
- retry count
- analysisMode
- 오류 category

저장 금지:

- API key
- Snapshot JSON 전체
- raw OpenAI response 전체
- 댓글·설명·보류 사유 원문
- 민감 데이터가 포함될 수 있는 SDK 역직렬화 예외 전문

운영 로그 레벨은 기본 INFO 이하로 두고 `OPENAI_LOG=debug`를 운영 기본값으로 사용하지 않는다.

# 11. Developer Prompt 계약

고정 Developer Prompt:

```text
당신은 팀 업무 회의를 지원하는 분석가다.

서버가 제공한 수치와 사실을 변경하거나 재계산하지 않는다.
입력에 존재하지 않는 업무, 사람, 날짜, 원인을 생성하지 않는다.
서버가 제공한 riskCandidates 안에서만 이슈를 선택한다.
가장 중요한 운영 병목 하나를 핵심 판단으로 선택한다.
성과는 최대 1개, 이슈와 결정은 최대 3개다.
모든 판단은 실제 evidenceTaskRefs를 가진다.
개인의 능력, 태도, 성실성을 평가하지 않는다.
팀장이 결정할 사항과 실행 담당자의 행동을 구분한다.
인과관계가 확정되지 않으면 원인으로 단정하지 않는다.
정보가 부족하면 INSUFFICIENT_EVIDENCE를 사용한다.
결정 옵션, 실행 단계, 완료 조건은 서버 허용 목록에서만 선택한다.
JSON Schema를 엄격히 준수한다.
```

프롬프트에는 다음을 넣지 않는다.

- HTML
- CSS
- PDF 문구
- 실제 사용자 이름
- API key
- 자유 입력 원문
- 장문의 제품 설명

# 12. 저장 계약

## 12.1 테이블

```text
ai_weekly_report_revision
```

D5 확정:

- 이 테이블을 `backend/src/main/resources/db/migration/V34__create_ai_weekly_report_revision.sql`
  로 **새로 추가**한다. 현재 Flyway head는 V33이다.
- 기존 `reports` 테이블은 **스키마도 데이터도 변환하지 않는다.** 기존 행은
  그대로 보존된다.
- 기존 AI 리포트 ID로 조회·다운로드를 시도하면
  `410 Gone` + 코드 `AI_REPORT_LEGACY_REVISION`을 반환한다. 이전 형식 리포트를
  v7-2 화면으로 렌더링하지 않는다. 이렇게 해야 두 계약이 동시에 활성 상태로
  남지 않는다.
- 롤백은 `DROP TABLE ai_weekly_report_revision`과 Flyway history 행 삭제로
  끝나며 기존 데이터 손실이 없다.

필드:

```sql
id BIGINT PRIMARY KEY
group_id BIGINT NOT NULL
period_from DATE NOT NULL
period_to_exclusive DATE NOT NULL
language VARCHAR(8) NOT NULL
revision INT NOT NULL
status VARCHAR(20) NOT NULL
analysis_mode VARCHAR(20) NOT NULL
snapshot_schema_version VARCHAR(80) NOT NULL
analysis_schema_version VARCHAR(80) NOT NULL
prompt_version VARCHAR(80) NOT NULL
model VARCHAR(120)
source_hash CHAR(64) NOT NULL
snapshot_json JSON NOT NULL
analysis_json JSON NOT NULL
validation_json JSON NOT NULL
failure_category VARCHAR(80)
generated_by_user_id BIGINT NOT NULL
created_at DATETIME(6) NOT NULL
finalized_at DATETIME(6)
```

## 12.2 상태

```text
GENERATING
FINALIZED
FAILED
```

D4 확정: 상태축은 이 하나뿐이다. **초안 편집과 수동 확정을 제거한다.**
생성이 성공하거나 서버 Fallback이 성공하면 즉시 `FINALIZED`이며, 팀장의 별도
확정 조작이 필요하지 않다.

기존 구현에서 제거되는 것:

```text
PATCH /api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/draft
POST  /api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/finalization
GET   /api/v1/groups/{groupId}/reports/ai-weekly/revisions
editorVersion (낙관적 잠금)
PublicationStatus (LEGACY / DRAFT / FINALIZED / SUPERSEDED 별도 축)
DRAFT 상태와 supersede 전이
프론트엔드 초안 편집기
```

재생성은 유지한다(§12.4).

사용자 요청은 OpenAI 실패로 FAILED가 되지 않는다.

OpenAI 실패 후 정상 Fallback 생성 시:

```text
status = FINALIZED
analysis_mode = SERVER_FALLBACK
```

FAILED는 Snapshot 생성, DB 저장, renderer 등 서버 내부 실패에만 사용한다.

## 12.3 중복 방지

source hash 입력:

```text
canonical snapshot JSON
+ prompt version
+ analysis schema version
+ model
```

동일 hash의 FINALIZED가 있고 `regenerate=false`이면 재사용한다.

## 12.4 재생성

```text
최초 생성           → revision 1, FINALIZED
regenerate=true     → revision 2, FINALIZED
동일 source 재요청   → 기존 FINALIZED 반환 (OpenAI 재호출 없음)
```

기존 revision은 삭제하지 않고 덮어쓰지도 않는다.

# 13. Java 패키지와 파일

```text
backend/src/main/java/com/teamproject/report/
├─ presentation/
│  └─ AiWeeklyReportController.java
├─ application/
│  ├─ AiWeeklyReportService.java
│  ├─ AiWeeklyReportSnapshotAssembler.java
│  ├─ AiWeeklyReportPolicyEngine.java
│  ├─ AiWeeklyReportAnalysisValidator.java
│  ├─ AiWeeklyReportViewProjector.java
│  ├─ AiWeeklyReportDocumentService.java
│  └─ port/
│     └─ AiWeeklyReportGateway.java
├─ application/dto/
│  └─ AiWeeklyReportDtos.java
├─ domain/
│  ├─ AiWeeklyReportRevision.java
│  ├─ AiWeeklyReportStatus.java
│  └─ AiAnalysisMode.java
└─ infrastructure/
   ├─ AiWeeklyReportRevisionRepository.java
   ├─ TaskEvidenceQueryRepository.java
   └─ openai/
      ├─ OpenAIConfiguration.java
      ├─ OpenAiReportProperties.java
      ├─ OpenAiReportPrompt.java
      ├─ OpenAiWeeklyReportGateway.java
      ├─ OpenAiAnalysisContractMapper.java
      ├─ OpenAiReportException.java
      └─ contract/
         └─ AiWeeklyReportAnalysisContract.java

backend/src/main/resources/db/migration/
└─ V34__create_ai_weekly_report_revision.sql

backend/src/main/resources/ai/
├─ v7-2-prompt-001.txt
└─ ai-weekly-report-analysis-v1.schema.json

backend/src/test/resources/ai/
├─ ai-weekly-report-snapshot-v1.example.json
├─ ai-weekly-report-analysis-v1.example.json
└─ fixtures/
```

`application`과 `domain`은 `com.openai.*` 타입을 import하지 않는다. 공식 SDK 의존은 `infrastructure.openai` 아래로 제한한다.

# 14. 서비스 오케스트레이션

```java
public AiWeeklyReportEnvelope generate(
        Long userId,
        Long groupId,
        GenerateAiWeeklyReportRequest request) {

    validatePeriod(request.from(), request.toExclusive());
    authorization.requireAiReportGeneration(userId, groupId);

    AiWeeklyReportSnapshotV1 snapshot =
            snapshotAssembler.assemble(userId, groupId, request);

    String sourceHash = fingerprint(
            snapshot,
            properties.promptVersion(),
            properties.model()
    );

    if (!request.regenerate()) {
        var existing = repository.findFinalizedBySourceHash(
                groupId,
                request.from(),
                request.toExclusive(),
                sourceHash
        );
        if (existing.isPresent()) {
            return mapper.toEnvelope(existing.get());
        }
    }

    Long revisionId = createGeneratingRevision(snapshot, sourceHash, userId);

    AnalysisResult result;
    try {
        AiWeeklyReportAnalysisV1 analysis = aiWeeklyReportGateway.analyze(snapshot);
        ValidationResult validation = analysisValidator.validate(snapshot, analysis);
        result = AnalysisResult.openAi(analysis, validation);
    } catch (RuntimeException exception) {
        AiWeeklyReportAnalysisV1 fallback = fallbackFactory.create(snapshot);
        ValidationResult validation = analysisValidator.validate(snapshot, fallback);
        result = AnalysisResult.fallback(
                fallback,
                validation,
                failureClassifier.categoryOf(exception)
        );
    }

    finalizeRevision(revisionId, result);
    return mapper.toEnvelope(repository.getRequired(revisionId));
}
```

트랜잭션 경계:

```text
1. Snapshot 생성 및 GENERATING revision 저장 → transaction 종료
2. 공식 OpenAI SDK 동기 호출 → DB transaction 밖
3. 결과 검증
4. FINALIZED 갱신 → 새 transaction
```

> 2026-08-02 현재 구현: 2·3은 지켜진다. 생성 메서드에 트랜잭션을 걸지 않아 OpenAI 호출
> 동안 커넥션을 잡지 않는다. 1·4의 **GENERATING revision 선저장은 미구현**이다. 완성된
> 결과를 한 번에 저장하고, revision 번호 충돌은 재시도로 처리한다.
>
> 선저장이 값어치를 가지려면 그 행을 읽는 쪽(폴링 엔드포인트나 진행 UI)이 있어야 한다.
> 지금은 없다. 도입하려면 `analysis_json`·`analysis_mode`를 nullable로 바꾸는 마이그레이션과
> 엔티티 가변화가 필요하므로, 비동기 전환과 함께 하는 것이 맞다.

주의:

- OpenAI 호출 동안 DB transaction과 connection을 잡지 않는다.
- 동일 그룹·기간 동시 생성은 DB unique key 또는 application lock으로 차단한다.
- application service는 `AiWeeklyReportGateway`만 호출하며 `OpenAIClient`를 직접 주입받지 않는다.
- 다운로드와 조회는 저장된 revision만 사용하고 SDK를 호출하지 않는다.

# 15. 권한

확정:

| 기능 | LEADER | MEMBER | 비멤버 |
|---|:---:|:---:|:---:|
| 생성·재생성 | O | X | X |
| 조회 | O | O | X |
| 다운로드 | O | O | X |
| Snapshot 원본 조회 | X | X | X |
| 분석 로그 조회 | 관리자 | X | X |

서버가 생성과 다운로드 시 그룹 membership을 다시 확인한다.

기존 구현의 다음 게이트는 **그대로 유지한다**.

- TEAM 그룹만 사용 가능 — 구현됨. `AiWeeklyReportAccessService`가 TEAM·PAID를 함께 보고
  `AI_REPORT_PAID_REQUIRED`로 거부한다(별도 `PERSONAL_GROUP_RESTRICTED` 분기는 두지 않는다)
- 유료 그룹만 사용 가능 (`AI_REPORT_PAID_REQUIRED`) — 구현됨
- 같은 기간 성공 생성 3회 상한 (`AI_REPORT_WEEKLY_LIMIT`) — 구현됨. `regenerate=true`일 때
  기간·언어별 revision 수를 세어 3 이상이면 `409`로 거부한다. 거부는 OpenAI 호출 전에 한다.

> 2026-08-02: 이 상한은 한 기간을 반복해 다시 만드는 낭비만 막는다. 기간을 옮겨 가며 부르는
> **총 지출은 묶이지 않는다.** 그룹 단위 지출 상한은 아직 없고, 값을 정하려면 실제 사용량이
> 필요하다. 같은 날 `input_tokens`·`output_tokens` 기록을 시작했다(V34에 컬럼은 이미 있었고
> 계속 null로 저장되고 있었다).

D4로 DRAFT 상태가 사라지므로, 팀원에게 미확정 리포트를 숨기던
`AI_REPORT_NOT_FINALIZED` 분기도 함께 사라진다. 생성이 끝나면 활성 팀원은
바로 조회·다운로드할 수 있다.

# 16. Fallback

OpenAI 실패 조건:

- `AI_REPORT_ENABLED=false` (`app.ai-report.enabled=false`)
- API key 또는 model 설정 누락
- SDK timeout
- SDK 내부 재시도 후 연결 실패
- 408·409·429·5xx
- refusal
- incomplete 또는 failed response
- Structured Outputs Java 역직렬화 실패
- SDK local JSON Schema 검증 실패
- 응답 message/outputText 없음
- 유효하지 않은 task/event/candidate ref
- 허용되지 않은 action code
- 잘못된 우선순위
- 입력에 없는 숫자·날짜 생성
- business validation 실패

Fallback 결과도 `ai-weekly-report-analysis.v1`을 만족한다.

Fallback 생성 규칙:

1. 위험 후보를 서버 precedence로 최대 3개 선택
2. template registry로 title·impact·decision 생성
3. 성과는 완료+체크리스트 완료 업무에서 최대 1개
4. 비교가 없으면 delta 문장 미표시
5. 근거 부족은 missingEvidence 표시
6. `analysisMode=SERVER_FALLBACK`
7. 다운로드 가능
8. 사용자 본문에는 기술 오류 대신 “기본 분석으로 생성됨” 표시
9. SDK 예외 전문은 사용자 응답과 사용자 PDF에 노출하지 않음
10. 원본 Snapshot과 raw API 응답을 오류 로그에 남기지 않음

# 17. 테스트 명세

## 단위 테스트

- Snapshot JSON Schema
- Analysis JSON Schema
- Java 계약 클래스 ↔ 저장 JSON Schema 공개 필드·enum 드리프트
- SDK Structured Outputs 계약 클래스 local validation
- 기간 유효성 검증 (`from < toExclusive`, 이미 끝난 기간)
- 현재/이전 기간 delta
- risk candidate 규칙
- severity·precedence 정렬
- ref 매핑
- option/step/completion code 부분집합
- priority 연속성
- deadline source 검증
- achievement 완료 상태 검증
- 개인정보 필드 미직렬화
- Fallback schema 적합성
- OpenAI SDK 예외 → 내부 오류 category 매핑
- SDK 예외 메시지 로그 redaction

## 통합 테스트

DB를 포함하고 `AiWeeklyReportGateway`만 Fake로 교체한다. 테스트에서 실제 OpenAI API를 호출하지 않는다.

| 시나리오 | 기대 |
|---|---|
| 정상 Structured Output | OPENAI, FINALIZED |
| Gateway timeout | SERVER_FALLBACK, FINALIZED |
| Gateway rate limit | SERVER_FALLBACK, FINALIZED |
| structured output 없음 | SERVER_FALLBACK, FINALIZED |
| 잘못된 taskRef | 결과 폐기, Fallback |
| 허용되지 않은 option | 결과 폐기, Fallback |
| priority P1 중복 | 결과 폐기, Fallback |
| NO_BASELINE인데 delta 사용 | 결과 폐기, Fallback |
| 업무 없음 | Gateway 0회, NO_ACTION_REQUIRED |
| 동일 snapshot 재요청 | Gateway 추가 호출 0회 |
| regenerate=true | revision 증가, Gateway 1회 |
| 다운로드 | Gateway 0회, 저장 revision 렌더링 |
| raw comment/name/description | OpenAI Snapshot JSON에 없음 |
| AI가 새 날짜 작성 | 검증 실패, Fallback |
| OpenAI 비활성화 | Gateway 0회, Fallback |
| model 설정 없음 | Gateway 0회 또는 즉시 실패 후 Fallback |

## SDK smoke test

실제 API 키 없이 실행:

- Spring context에서 `OpenAIClient` Bean 하나만 생성
- `OpenAiReportProperties` binding 검증
- timeout 45초 적용
- maxRetries 1 적용
- application/domain 패키지에서 `com.openai.*` import가 없는지 ArchUnit 또는 정적 검사
- Maven dependency tree에 `openai-java`가 `backend/pom.xml`이 지정한 버전으로 하나만 존재
- EOL Starter 의존성이 존재하지 않음
- Jackson 호환성 검사 비활성화 옵션을 사용하지 않음

실제 API 키 수동 검증은 별도 프로파일에서 1건만 수행하고 CI 필수 테스트로 만들지 않는다.

## HTML 회귀 테스트

- A4 4페이지 역할 유지
- page 1 기본 스타일 불변
- issue 최대 3개
- 정상 팀원 전체표 없음
- 기술 용어 없음
- `Structured Outputs`, model id, token 수를 사용자 본문에 표시하지 않음
- 모든 AI 카드에 근거 ref 표시
- HTML escape 적용
- null 데이터 표시 규칙

## 실행 명령

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml dependency:tree -Dincludes=com.openai
mvn -f backend/pom.xml dependency:tree -Dincludes=com.fasterxml.jackson.core
mvn -f backend/pom.xml spring-boot:run
```

수동 API 검증:

```bash
curl -X POST "http://localhost:8080/api/v1/groups/7/reports/ai-weekly" \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "from":"2026-07-20",
    "toExclusive":"2026-07-27",
    "language":"KO",
    "regenerate":false
  }'
```

# 18. 완료 조건

v7-2 구현은 다음이 모두 참일 때 완료다.

- 4페이지 구조가 유지된다. 페이지 제목의 기간 표현은 실제 기간을 따른다(주간·월간·연간·기간).
- 페이지 1은 기존 기본 리포트와 동일한 서버 수치를 사용한다.
- 공식 `com.openai:openai-java`를 `backend/pom.xml`이 지정한 버전으로 사용한다.
- EOL된 `openai-java-spring-boot-starter`와 직접 `RestClient` 호출을 사용하지 않는다.
- `OpenAIClient`는 singleton Bean 하나로 공유된다.
- Responses API와 Java 타입 기반 Structured Outputs를 사용한다.
- OpenAI 입력에 원문 댓글·설명·이름·업무 제목·일정 제목이 없다.
- `safeLabel`이 서버 생성 비식별 의미 라벨이며 원본 제목 조각을 포함하지 않는다.
- 기존 `app.ai-report.*` 설정 키와 환경변수 이름이 유지된다.
- 다운로드가 `/{reportId}/download`에서 인쇄용 HTML을 반환한다 (D7 2026-08-01 개정).
- 초안 편집·수동 확정 경로가 제거되고 생성 즉시 FINALIZED가 된다.
- 기존 AI 리포트 ID 접근이 `410 AI_REPORT_LEGACY_REVISION`을 반환한다.
- OpenAI 출력은 SDK 역직렬화와 business validation을 통과한다.
- 모든 AI 판단은 실제 candidate와 task ref에 연결된다.
- 수치·날짜는 renderer가 서버 데이터에서 삽입한다.
- AI가 결정 옵션과 실행 코드를 허용 목록 밖에서 선택할 수 없다.
- OpenAI 실패 시에도 FINALIZED 리포트가 생성된다.
- 같은 snapshot 재요청은 OpenAI를 재호출하지 않는다.
- 다운로드는 저장 revision을 사용한다.
- SDK timeout·재시도·Jackson 호환성이 테스트된다.
- 테스트에서 정상·실패·근거 부족·재생성·다운로드가 검증된다.
- 사용자 리포트에 개발 기술 용어가 노출되지 않는다.

> §19 구현 순서와 §20 Agent 실행 지침은 2026-08-02에 제거했다. 구현이 끝난 뒤에는 계약이
> 아니라 작업 지시였고, 브랜치 규칙·도구 사용법은 `AGENTS.md`와 중복이었다. 참조하던
> `WorkTaskFlow_v7-2_..._수정본.md`는 저장소에 없다. 진행 기록은 ExecPlan에 있다.

# 21. 확정된 제품 결정

이 절의 항목은 모두 확정되었다. 구현 중 임의로 바꾸지 않는다. 변경이
필요하면 구현을 멈추고 계획 변경 승인을 받는다.

## 21.1 보류 category — 확정

```text
MVP: tasks.blocker_type(V32 컬럼)을 history.holdReasonCategory enum으로 매핑한다.
     매핑되지 않는 값은 UNKNOWN으로 전송한다.
     자유 입력 보류 사유 원문은 전송하지 않는다.
후속: task 도메인에 구조화 blocker category를 추가하는 작업은 별도 이슈로 분리한다.
```

## 21.2 생성 권한 — 확정

```text
생성·재생성: LEADER
조회·다운로드: 활성 그룹 MEMBER
전제: TEAM 그룹 + 유료 플랜 (기존 게이트 유지)
```

## 21.3 D1~D7 확정 결과

| 결정 | 확정안 |
|---|---|
| D1 분석 상태 | JSON Schema 기준 `NORMAL / PARTIAL / NO_ACTION_REQUIRED` |
| D2 성과 | `achievement`는 필수 객체. 없으면 `status = NONE` |
| D2 개인정보 | `safeLabel`에 원본 업무·일정 제목을 넣지 않는다. 서버 생성 비식별 의미 라벨만 사용 |
| D3 기간 | 그룹 시간대 기준 완료된 `[from, toExclusive)`. 시작 요일·길이 제한 없음 (2026-07-31 개정) |
| D4 lifecycle | 초안 편집·수동 확정 제거. 생성 또는 fallback 성공 시 즉시 `FINALIZED`. 재생성은 유지 |
| D5 저장소 | `V34` 신규 `ai_weekly_report_revision` 테이블. 기존 `reports` 데이터 변환 금지 |
| D5 legacy | 기존 AI 리포트 ID 접근 시 `410 AI_REPORT_LEGACY_REVISION` |
| D6 설정 | 기존 `app.ai-report.*`와 기존 환경변수 이름 유지. SDK 버전과 실행 정책만 변경 |
| D7 다운로드 | 실제 PDF 유지. 기존 `/pdf`와 `application/pdf` 유지 |

# 22. 최종 정의

> v7-2 AI 리포트는 서버가 확정한 그룹 업무 사실에서 최대 3개의 회의 안건을 선택하고, 각 안건을 근거 업무·관찰된 영향·팀장 결정·실행 단계·완료 조건으로 연결하는 4페이지 리포트다. 기간은 주간에 한정되지 않는다(D3).

OpenAI API의 역할은 문장을 화려하게 만드는 것이 아니다.

```text
확정된 업무 사실
→ 회의에서 중요한 병목 선택
→ 관련 근거 통합
→ 팀장이 내려야 할 결정
→ 실행 주체·기한 출처·완료 조건 연결
```

이 경계를 벗어나는 수치 계산, 날짜 생성, 개인 평가, 자동 배정은 v7-2 범위에 포함하지 않는다.
