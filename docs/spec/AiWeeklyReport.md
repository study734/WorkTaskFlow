# WorkTaskFlow AI 주간 리포트 v7-2
## 실제 JSON 계약 및 구현 명세

- 상태: 구현 기준안
- 보고서 뼈대: v7 FROZEN
- 사용자 표현: v7-2
- 입력 계약: `ai-weekly-report-snapshot.v1`
- OpenAI 출력 계약: `ai-weekly-report-analysis.v1`
- 프롬프트 버전: `v7-2-prompt-001`
- OpenAI 연동: 공식 `com.openai:openai-java:4.47.0`
- OpenAI API: Responses API + Java 타입 기반 Structured Outputs
- Spring 연동: `OpenAIClient` Bean 직접 등록. EOL된 Spring Boot Starter 사용 금지
- 명세 수정일: 2026-07-31
- 핵심 원칙: 서버가 사실·수치·날짜·후보를 확정하고, AI는 중요도·연결·설명·권고만 수행한다.
- 계약 정본: 런타임 출력 구조는 Java 계약 클래스, 외부 공유·회귀 검증은 버전 고정 JSON Schema, 의미 규칙은 서버 Validator가 담당한다.

---

## 수정 이력 — 공식 OpenAI Java SDK 반영

- `Spring RestClient` 직접 호출 권고 제거
- 공식 `com.openai:openai-java:4.47.0` 채택
- Responses API + Java 타입 기반 Structured Outputs로 변경
- Spring Boot 2.7 전용 EOL Starter 사용 금지 명시
- `OpenAIClient` singleton Bean과 timeout/retry 정책 추가
- SDK 전용 contract·adapter를 infrastructure 계층으로 격리
- JSON Schema와 Java 계약 드리프트 테스트 추가
- Fake Gateway 기반 통합 테스트로 변경
- Agent 실행 지침에 GitHub MCP, Context7 MCP, Maven CLI 추가

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
공식 OpenAI Java SDK 4.47.0
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

- 사용: `com.openai:openai-java:4.47.0`
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
| 2 | 이번 주 핵심 | 서버 + AI |
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

## 2.3 페이지 2 — 이번 주 핵심

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
  "generatedAt": "2026-07-27T00:05:00Z",
  "language": "KO",
  "group": {},
  "period": {},
  "metrics": {},
  "comparison": {},
  "workflow": {},
  "members": [],
  "tasks": [],
  "calendarEvents": [],
  "riskCandidates": [],
  "policy": {}
}
```

## 4.3 그룹·기간

```json
{
  "group": {
    "ref": "GROUP-7",
    "type": "TEAM",
    "timezone": "Asia/Seoul"
  },
  "period": {
    "from": "2026-07-20",
    "toExclusive": "2026-07-27",
    "durationDays": 7
  }
}
```

규칙:

- 기간은 `[from, toExclusive)`
- 주간 기간은 정확히 7일
- 날짜 계산은 그룹 timezone 기준
- OpenAI가 기간을 재해석하지 않음

## 4.4 Metrics

```json
{
  "metrics": {
    "periodTaskCount": 12,
    "completionRatePercent": 42,
    "onTimeRatePercent": 75,
    "delayedTaskCount": 2,
    "averageCompletionHours": 34,
    "statusCounts": {
      "requested": 2,
      "todo": 2,
      "inProgress": 2,
      "onHold": 1,
      "completed": 5,
      "rejected": 0,
      "cancelled": 0
    }
  }
}
```

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
    "previousPeriod": {
      "from": "2026-07-13",
      "toExclusive": "2026-07-20"
    },
    "deltas": {
      "periodTaskCount": 2,
      "completionRatePoint": -8,
      "onTimeRatePoint": -8,
      "delayedTaskCount": 1
    }
  }
}
```

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
    "approvedUnassigned": 1,
    "assignedTodo": 1,
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
  "ref": "MEMBER-3",
  "role": "MEMBER",
  "assignedCount": 4,
  "activeCount": 3,
  "completedCount": 1,
  "delayedCount": 1,
  "dueSoonCount": 2,
  "calendarConflictCount": 0
}
```

팀원 추천은 다음 정보가 존재하는 경우만 허용한다.

- activeCount
- dueSoonCount
- calendarConflictCount

AI는 개인의 능력·성실성·태도를 평가하지 않는다.

## 4.8 Task evidence

```json
{
  "ref": "TASK-104",
  "safeLabel": "사용자 테스트 결과 반영",
  "category": "QA",
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
    "linkedResourceCount": 0
  },
  "history": {
    "lastTransition": "REQUESTED_TO_TODO",
    "lastTransitionAt": "2026-07-22T11:30:00+09:00",
    "reopenCount": 0
  },
  "blocker": {
    "category": "NONE",
    "hasRawReason": false
  },
  "signals": [
    "APPROVED_UNASSIGNED",
    "OVERDUE",
    "CHECKLIST_NOT_STARTED",
    "RESOURCE_MISSING"
  ]
}
```

### 개인정보 경계

전송 금지:

- 실제 이름
- 댓글 원문
- 업무 description 원문
- 첨부파일 본문
- 자유 입력 보류 사유 원문
- 이메일·전화번호
- 사용자 ID

허용:

- 익명 ref
- 개인정보 필터를 거친 safeLabel
- 구조화 category
- 상태·수치·날짜
- 집계된 협업 신호

## 4.9 Calendar event

```json
{
  "ref": "EVENT-14",
  "type": "MEETING",
  "safeLabel": "전체 리허설",
  "startAt": "2026-07-31T14:00:00+09:00",
  "endAt": "2026-07-31T16:00:00+09:00",
  "ownerRef": "MEMBER-1",
  "linkedTaskRefs": ["TASK-104"]
}
```

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
  "analysisStatus": "COMPLETE",
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
- achievement: 0~1개
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
- memberRef가 Snapshot members에 존재
- eventRef가 Snapshot calendarEvents에 존재
- metricRef가 허용된 metric path

## 7.2 Candidate subset 검증

AI가 선택한 다음 값은 candidate 허용 목록의 부분집합이어야 한다.

- recommendedOptionCode
- executionStepCodes
- completionSignalCodes

## 7.3 상태 검증

- 성과 taskRef는 실제 `COMPLETED`
- 미할당 위험은 assigneeRef가 null
- 지연 위험은 dueState가 `OVERDUE`
- 보류 위험은 status가 `ON_HOLD`
- 체크리스트 위험은 server checklist 값과 일치

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
- 기간 7일
- language: KO 또는 EN
- group timezone 기준 완료된 기간
- 생성 권한 필요

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
  "downloadUrl": "/api/v1/groups/7/reports/ai-weekly/91/download"
}
```

HTTP:

- 새 생성: `201 Created`
- 동일 결과 재사용: `200 OK`
- 생성 진행 중: `202 Accepted`

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

규칙:

- FINALIZED만 다운로드
- 저장 revision 사용
- OpenAI 재호출 금지
- `Cache-Control: private, no-store`
- MVP Content-Type: `text/html;charset=UTF-8`
- 파일명: `toesa-ai-weekly-{groupId}-{from}-{toInclusive}-ko.html`

실제 PDF 엔진 도입은 v7-2 계약의 필수 범위가 아니다.

# 10. 공식 OpenAI Java SDK 연동

## 10.1 Maven 의존성

현재 공식 SDK 버전은 2026-07-31 확인 기준 `4.47.0`이다.

```xml
<properties>
    <openai-java.version>4.47.0</openai-java.version>
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

`.env` 또는 실행 환경:

```env
OPENAI_API_KEY=sk-proj-...
OPENAI_REPORT_ENABLED=false
OPENAI_REPORT_MODEL=<validated-model-id>
OPENAI_REPORT_TIMEOUT_SECONDS=45
OPENAI_REPORT_MAX_RETRIES=1
OPENAI_REPORT_MAX_OUTPUT_TOKENS=3000
```

`application.yml`:

```yaml
openai:
  report:
    enabled: ${OPENAI_REPORT_ENABLED:false}
    model: ${OPENAI_REPORT_MODEL:}
    prompt-version: v7-2-prompt-001
    timeout: ${OPENAI_REPORT_TIMEOUT_SECONDS:45s}
    max-retries: ${OPENAI_REPORT_MAX_RETRIES:1}
    max-output-tokens: ${OPENAI_REPORT_MAX_OUTPUT_TOKENS:3000}
```

규칙:

- API 키는 `OPENAI_API_KEY`에서 SDK가 읽는다.
- API 키를 `application.yml`, 로그, DB, Snapshot에 저장하지 않는다.
- 모델 ID는 코드 상수 대신 설정으로 주입한다.
- 발표·운영 검증 시에는 평가를 통과한 모델 snapshot을 고정한다.
- `OPENAI_REPORT_ENABLED=false`이면 API 호출 없이 Fallback을 사용한다.

## 10.3 설정 Properties

```java
package com.teamproject.report.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "openai.report")
public record OpenAiReportProperties(
        boolean enabled,
        String model,
        String promptVersion,
        Duration timeout,
        int maxRetries,
        long maxOutputTokens
) {
    public OpenAiReportProperties {
        if (timeout == null) timeout = Duration.ofSeconds(45);
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
                .fromEnv()
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .responseValidation(true)
                .build();
    }
}
```

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
    public Optional<Achievement> achievement;
    public List<Issue> issues;
    public List<String> globalMissingEvidence;

    public enum AnalysisStatus {
        COMPLETE,
        PARTIAL,
        NO_ACTION_REQUIRED
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
- 선택 값은 `Optional<T>` 사용
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
            throw new OpenAiReportUnavailableException("OPENAI_REPORT_MODEL is missing");
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

- 실제 SDK 4.47.0의 컴파일 타입을 IDE와 공식 예제로 확인한 뒤 import를 확정한다.
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

권장 필드:

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
regenerate=false → 기존 FINALIZED 재사용
regenerate=true  → revision + 1
```

기존 revision은 삭제하지 않는다.

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
└─ Vxx__create_ai_weekly_report_revision.sql

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

주의:

- OpenAI 호출 동안 DB transaction과 connection을 잡지 않는다.
- 동일 그룹·기간 동시 생성은 DB unique key 또는 application lock으로 차단한다.
- application service는 `AiWeeklyReportGateway`만 호출하며 `OpenAIClient`를 직접 주입받지 않는다.
- 다운로드와 조회는 저장된 revision만 사용하고 SDK를 호출하지 않는다.

# 15. 권한

MVP 권장:

| 기능 | LEADER | MEMBER | 비멤버 |
|---|:---:|:---:|:---:|
| 생성·재생성 | O | X | X |
| 조회 | O | O | X |
| 다운로드 | O | O | X |
| Snapshot 원본 조회 | X | X | X |
| 분석 로그 조회 | 관리자 | X | X |

서버가 생성과 다운로드 시 그룹 membership을 다시 확인한다.

# 16. Fallback

OpenAI 실패 조건:

- `OPENAI_REPORT_ENABLED=false`
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
- 기간 7일 검증
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
- Maven dependency tree에 `openai-java` 4.47.0이 하나만 존재
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

- v7의 4페이지 구조가 유지된다.
- 페이지 1은 기존 기본 리포트와 동일한 서버 수치를 사용한다.
- 공식 `com.openai:openai-java:4.47.0`을 사용한다.
- EOL된 `openai-java-spring-boot-starter`와 직접 `RestClient` 호출을 사용하지 않는다.
- `OpenAIClient`는 singleton Bean 하나로 공유된다.
- Responses API와 Java 타입 기반 Structured Outputs를 사용한다.
- OpenAI 입력에 원문 댓글·설명·이름이 없다.
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

# 19. 구현 순서

1. `pom.xml`에 `com.openai:openai-java:4.47.0` 추가
2. Maven dependency tree와 Jackson 호환성 확인
3. JSON Schema와 fixture를 테스트 리소스에 고정
4. `AiWeeklyReportAnalysisContract`와 contract drift test 구현
5. `AiWeeklyReportGateway` 포트와 Fake Gateway 구현
6. Snapshot assembler 구현
7. bulk evidence query 구현
8. policy engine으로 risk candidate 생성
9. server fallback 구현
10. business validator 구현
11. `OpenAIClient` Bean과 공식 SDK adapter 구현
12. revision 저장 구현
13. generate/read/download API 구현
14. v7-2 renderer 구현
15. 통합·회귀·실패 테스트
16. 실제 API 키로 수동 검증 1건

OpenAI 연결보다 Fallback, Fake Gateway, Validator를 먼저 구현한다. 이 순서이면 API 키가 없어도 v7-2 전체 흐름과 HTML을 검증할 수 있다.

SDK adapter 구현 시 공식 `openai/openai-java`의 `ResponsesStructuredOutputsExample`과 현재 버전 Javadocs를 기준으로 컴파일 타입을 확인한다.

# 20. Agent 실행 지침

```text
목표:
WorkTaskFlow main의 현재 기본 리포트 동작을 보존하면서,
v7-2 그룹 AI 주간 리포트의 snapshot/analysis JSON 계약,
server fallback, 공식 OpenAI Java SDK 기반 Responses API,
저장 revision, 4페이지 HTML 다운로드를 구현한다.

컨텍스트:
- backend/pom.xml
- backend/src/main/java/com/teamproject/report/application/ReportDocumentService.java
- backend/src/main/java/com/teamproject/report/presentation/ReportController.java
- backend/src/main/java/com/teamproject/dashboard/application/dto/DashboardDtos.java
- backend/src/main/java/com/teamproject/task/application/dto/TaskDtos.java
- backend/src/main/java/com/teamproject/calendar/application/dto/CalendarDtos.java
- ai-weekly-report-snapshot-v1.schema.json
- ai-weekly-report-analysis-v1.schema.json
- WorkTaskFlow_v7-2_JSON계약_구현명세_공식OpenAIJavaSDK수정본.md

도구:
- GitHub MCP로 현재 repo 파일과 member1 브랜치 상태를 먼저 확인한다.
- Context7 MCP에서 `openai/openai-java` 4.47.0의 Responses API와
  `ResponsesStructuredOutputsExample`을 확인한다.
- Maven CLI로 의존성·테스트·실행을 검증한다.

필수 CLI:
mvn -f backend/pom.xml dependency:tree -Dincludes=com.openai
mvn -f backend/pom.xml dependency:tree -Dincludes=com.fasterxml.jackson.core
mvn -f backend/pom.xml test
git diff --check

제약:
- main 브랜치 직접 작업 금지. 사용자에게 허용된 member1 브랜치만 사용.
- 기존 /reports/download 응답과 기본 HTML 스타일을 변경하지 않는다.
- 공식 `com.openai:openai-java:4.47.0`을 사용한다.
- `openai-java-spring-boot-starter` 사용 금지.
- OpenAI 직접 HTTP `RestClient` 구현 금지.
- application/domain 계층에서 `com.openai.*` import 금지.
- `OpenAIClient` singleton Bean을 infrastructure에서 직접 등록한다.
- Responses API + Java 타입 기반 Structured Outputs를 사용한다.
- 먼저 테스트를 추가하고 기존 출력 회귀를 고정한다.
- 원문 댓글, 업무 설명, 이름, 첨부 본문을 OpenAI에 보내지 않는다.
- OpenAI 출력은 렌더링 전에 SDK 구조 역직렬화와 business validation을 통과해야 한다.
- OpenAI 실패 시 SERVER_FALLBACK으로 FINALIZED한다.
- 업무 상태 변경 side effect를 만들지 않는다.
- SDK 예외 전문과 raw OpenAI response를 로그에 남기지 않는다.

완료:
- mvn -f backend/pom.xml test 통과
- dependency tree에서 openai-java 4.47.0 하나만 확인
- EOL Starter 의존성 없음
- 정상/timeout/invalid ref/no baseline/duplicate request/download 테스트 통과
- 동일 snapshot 재요청 시 OpenAI Gateway 호출 0회
- v7-2 HTML 4페이지 정보 구조 확인
- git diff --check 통과
- 변경 파일, 테스트 결과, SDK 버전, 남은 위험을 보고한다.
```

# 21. 확정이 필요한 제품 결정

## 21.1 보류 category

선택지:

1. task 도메인에 구조화 blocker category 추가
2. 기존 자유 입력 reason을 서버에서 제한적으로 분류
3. MVP에서는 `UNKNOWN`만 전송

권장:

```text
MVP: UNKNOWN 또는 명시 구조화 필드만 사용
후속: task 도메인에 blocker category 추가
```

## 21.2 생성 권한

선택지:

1. LEADER 전용
2. 그룹 리포트 조회 권한과 동일

권장:

```text
생성·재생성: LEADER
조회·다운로드: 활성 그룹 MEMBER
```

# 22. 최종 정의

> v7-2 AI 주간 리포트는 서버가 확정한 그룹 업무 사실에서 최대 3개의 회의 안건을 선택하고, 각 안건을 근거 업무·관찰된 영향·팀장 결정·실행 단계·완료 조건으로 연결하는 4페이지 주간 리포트다.

OpenAI API의 역할은 문장을 화려하게 만드는 것이 아니다.

```text
확정된 업무 사실
→ 회의에서 중요한 병목 선택
→ 관련 근거 통합
→ 팀장이 내려야 할 결정
→ 실행 주체·기한 출처·완료 조건 연결
```

이 경계를 벗어나는 수치 계산, 날짜 생성, 개인 평가, 자동 배정은 v7-2 범위에 포함하지 않는다.
