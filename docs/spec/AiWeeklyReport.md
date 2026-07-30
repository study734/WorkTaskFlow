# AI 주간 리포트 계약

상태: 로컬 알파 구현 기준(Flyway V23~V26)

## 기능 개요

AI 주간 리포트는 완료된 한 주의 업무 활동을 frozen snapshot으로 집계하고, 익명화된 문맥만 OpenAI
provider에 보내 운영 요약 초안을 만든다. 팀장은 초안을 편집·재생성·확정할 수 있고, 확정된 revision만
팀원에게 공개한다.

- 기본 PDF는 AI와 독립된 무료 보고서 경로다.
- AI 보고서는 PAID TEAM 전용이며 같은 그룹·주차·언어의 완료 결과를 캐시한다.
- 대시보드에는 별도 AI 탭이나 AI 요약 대시보드를 두지 않는다. 기본 리포트 카드의
  범위·기간을 PDF와 `AI 리포트` 행동이 함께 사용하며 독립 날짜 입력을 제공하지 않는다.
  현재 AI 생성 계약은 `GROUP`·`WEEKLY` 기본 리포트의 완료된 월요일~일요일 범위를 확장한다.
- `AI 리포트` 행동은 기본 리포트의 frozen 수치·업무 흐름·근거를 AI가 해석한 리포트 화면을
  바로 연다. 저장 리포트가 없으면 같은 행동에서 생성한 뒤 해당 화면으로 이동한다.
- 상세 경로는 별도 AI 대시보드가 아니라 문서형 리포트 reader다. frozen 리포트의 headline을
  문서 제목으로 사용하고, 보기 방식·리비전·scope는 본문보다 낮은 우선순위의 도구막대로 둔다.
  편집·재생성·확정·PDF 행동과 publication lifecycle은 그대로 유지한다.
- 팀장의 기본 리포트 범위는 `GROUP`을 우선값과 첫 옵션으로 표시한다. AI 생성 계약을 지원하지
  않는 `MY` 또는 월간·연간 선택에서도 버튼 아래 별도 설명문으로 컨트롤 행 높이를 바꾸지 않고,
  비활성 버튼의 접근성 설명으로 지원 범위를 안내한다.
- 공개 랜딩의 AI 미리보기는 고정 예시다. 로그인 API, 실제 그룹 데이터, OpenAI 호출을 사용하지 않는다.
- 리포트 근거에는 구조화 blocker와 주간 목표가 포함된다.

`HOLD` 요청은 자유 입력 `reason` 외에 다음 구조화 필드를 저장한다.

- `blockerType`: `DEPENDENCY`, `DECISION`, `ACCESS`, `RESOURCE`, `TECHNICAL`, `EXTERNAL`, `OTHER`
- `blockerNextActionType`: `FOLLOW_UP`, `ESCALATE`, `DECIDE`, `UNBLOCK_ACCESS`, `REPLAN`,
  `WAIT_EXTERNAL`, `OTHER`
- `blockerReviewDate`: 그룹 현지 날짜

주간 목표는 그룹·주차별 최대 3개다. LEADER가 목표를 관리하고 LEADER 또는 업무 담당자가 같은 주의
업무를 목표에 연결한다.

## 권한

| 기능 | 비멤버 | MEMBER | LEADER | 조건 |
|---|:---:|:---:|:---:|---|
| 기본 PDF `MY` | X | O | O | 활성 TEAM 멤버 |
| 기본 PDF `GROUP` | X | X | O | FREE 그룹은 주간 한도 적용 |
| AI 리포트 생성·편집·재생성·확정 | X | X | O | PAID TEAM |
| AI draft·revision 목록 조회 | X | X | O | PAID TEAM |
| finalized AI 상세·PDF | X | O | O | 활성 PAID TEAM 멤버 |
| 주간 목표 조회 | X | O | O | 활성 멤버 |
| 주간 목표 CRUD | X | X | O | 종료된 주 편집 불가 |
| 업무-목표 연결 | X | 담당자 O | O | 같은 그룹·주차 |

프런트의 버튼 숨김과 관계없이 서버가 활성 멤버십, TEAM 유형, PAID 플랜, 역할, publication status를
다시 검사한다. MEMBER가 draft에 접근하면 `AI_REPORT_NOT_FINALIZED`로 거부한다.

## 생성·수정·확정 생명주기

생성 상태와 공개 상태는 독립된 두 상태축이다.

```text
생성: GENERATING → COMPLETED
                └→ FAILED

공개: DRAFT → FINALIZED → SUPERSEDED
          └── 재생성 → 새 revision의 DRAFT
```

1. 생성 요청은 짧은 DB 트랜잭션에서 lease와 attempt를 획득한다.
2. 외부 provider 호출은 DB 잠금 밖에서 수행한다.
3. 완료·실패 반영은 attempt 소유권을 확인한다. lease가 만료된 stale worker는 새 결과를 덮어쓸 수 없다.
4. 편집은 `expectedEditorVersion` 낙관적 잠금으로 충돌을 막는다.
5. 재생성은 기존 데이터를 덮어쓰지 않고 `source_report_id`를 가진 새 revision을 만든다.
6. 새 revision을 확정하면 같은 시리즈의 기존 `FINALIZED`는 삭제하지 않고 `SUPERSEDED`로 바뀐다.

같은 그룹·주차·언어에서 성공한 생성·재생성은 합계 3회까지다. 실패 revision은 성공 한도를 차감하지
않으며, 완료된 동일 요청은 캐시된 결과를 `200 OK`로 재사용한다.

## BASELINE과 frozen evidence

`comparison.available=false`인 `BASELINE`은 “변화량 0”이 아니라 신뢰할 이전 이력이 부족하다는 뜻이다.
숫자 증감 대신 비교 불가 상태를 화면과 PDF에 표시한다.

`task_activity_events`는 업무 변경 순간의 상태, 우선순위, 담당자, 체크리스트, blocker, 목표 연결을
비정규화한 frozen snapshot이다. `history_complete=false`는 이벤트 도입 이전 이력까지 완전하게
재현할 수 없음을 나타낸다. 생성된 revision의 metrics, AI context, reference index, evidence도 함께
동결해 이후 업무 수정이 과거 리포트를 바꾸지 않게 한다.

발급하는 evidence key 계열은 다음과 같다. 서술의 모든 숫자·날짜는 이 키의 placeholder로만 표기한다.

| 계열 | 키 | 비고 |
|---|---|---|
| 상태 개수 | `tasks.total\|completed\|active\|onHold\|delayed\|highPriority\|requested\|todo\|inProgress\|rejected\|cancelled` | |
| 체크리스트 | `checklist.total\|completed` | |
| 비율 | `rates.completion\|onTime\|checklistCompletion` | 값이 없으면 미발급 |
| 소요시간 | `time.averageCompletionHours` | 완료 업무가 없으면 미발급 |
| 주간 비교 | `comparison.tasksTotalDelta\|completedDelta\|delayedDelta\|onHoldDelta\|completionRateDelta\|checklistRateDelta\|onTimeRateDelta\|avgCompletionHoursDelta` | `BASELINE`에서는 전량 미발급 |
| 일별 | `daily.<ISO date>.created\|completed` | 기간의 7일 |
| 흐름 요약 | `flow.peakCompletedDay\|peakCompletedCount\|zeroCompletionDays` | 완료가 0이면 peak 미발급 |
| 업무 | `task.TASK-NN.dueDate\|blockerReviewDate\|checklistTotal\|checklistCompleted` | 해당 값이 있는 업무만 |
| 목표 | `objective.GOAL-NN.tasks\|completed\|onHold\|delayed\|active` | |
| 정체 | `task.TASK-NN.blockedHours\|approvalWaitHours\|startLagHours\|reopenCount\|assigneeChangeCount\|idleDays` | 값이 0이면 미발급. `idleDays`는 **비종결 업무만** |
| 흐름 정체 | `flow.longestBlockedHours\|longestApprovalWaitHours\|idleOverThreeDays\|reopenedTaskCount\|overdueReviewCount` | 값이 0이면 미발급 |
| 팀원 | `member.MEMBER-NN.assigned\|active\|completed\|delayed\|onHold\|checklistTotal\|checklistCompleted\|completionRate\|onTimeRate\|checklistRate` | |
| 팀원 성과 | `member.MEMBER-NN.score\|grade\|rank`, `members.ratedCount\|topGrade\|lowestGrade` | 「팀원 성과 등급」 참조 |
| 이력 | `coverage.partial` | 부분 이력일 때만 |

### 정체 시간 계산

`task_activity_events`의 `task_status`는 그 변경 **이후**의 상태이므로, 한 이벤트부터 다음 이벤트까지가
그 상태의 체류 구간이다. 마지막 구간은 기간 종료 시각에서 끊어 동결 값이 재현되게 한다.
`occurred_at`은 UTC로 일관 저장되고 두 값의 **차이만** 사용하므로 서버 시간대 영향이 없다.

체류 시간은 **기간 시작 이전 구간까지 포함**한다 — 최신 스냅샷을 뽑을 때 이미 조회하는 결과집합
(`findAllByTaskIdInAndOccurredAtLessThan...`)을 재사용하므로 추가 쿼리가 없다. 이 때문에 "3주째 막혀
있다"를 말할 수 있다.

`flow.overdueReviewCount`는 그룹 시간대의 **기간 종료일** 기준으로 판정한다. 실행 시각을 쓰면 같은
리포트를 나중에 다시 열 때 값이 달라진다.

### 팀원 성과 등급

서버가 동결된 `metrics.members[]`만으로 계산하며 부동소수를 쓰지 않는다(같은 입력은 항상 같은 등급).

```
completionRate / onTimeRate / checklistRate 중 산출된 항목만 가중 평균
  가중치 45 / 30 / 25
penalty = min(20, 지연비중/2) + min(10, 5 × 보류건수)
score   = clamp(가중평균 − penalty, 0, 100)
grade   = A≥85 · B≥70 · C≥55 · D≥40 · E<40
rank    = score 내림차순, 동점은 완료 많은 순 → 지연 적은 순 → 별칭 순. 표준 경쟁 순위(1,2,2,4)
```

배정 업무가 없거나 산출 근거가 하나도 없으면 `NOT_RATED`이며 순위에서 제외하고, 화면은 낮은 등급이
아니라 "평가 대상 아님"으로 표시한다. 등급은 evidence에 `member.*.grade` 키가 있을 때만 노출하므로
이 규칙 이전에 생성된 리포트에는 등급이 나타나지 않는다(구 `metrics_json`의 0/null로 등급을 지어내지
않기 위한 장치).

`task.TASK-NN.*`는 그 업무만, `objective.GOAL-NN.*`는 그 목표에 연결된 업무만 `taskRefs`로 참조할 수
있다. 상태·흐름 키도 해당 상태의 업무만 참조할 수 있고, 위반하면 생성은 거부되고 저장본은 참조에서
제외된다.

## 기본 PDF와 AI PDF

### 기본 PDF

- `MY`: 활성 MEMBER와 LEADER가 자신의 범위를 다운로드한다.
- `GROUP`: LEADER만 다운로드한다.
- FREE TEAM의 `GROUP` 다운로드는 주간 2회 한도를 적용한다.
- 권한·한도를 검증하고 PDF 렌더링이 성공한 뒤에만 사용량을 기록한다. 실패 다운로드는 차감하지 않는다.

### AI PDF

- `FINALIZED` revision만 서버 생성 PDF attachment로 다운로드한다.
- 활성 PAID TEAM MEMBER와 LEADER가 접근할 수 있다.
- 응답은 `Content-Type: application/pdf`, UTF-8 파일명의 `Content-Disposition: attachment`를 사용한다.
- 브라우저 `/print` 화면은 인쇄 대화상자용이며 서버 PDF 다운로드와 별도 동작이다.
- PDF는 포함된 `NanumGothic` 글꼴을 사용해 한국어를 렌더링한다.

## Prototype 기반 표시 계약

이 절의 상태 태그는 새로 추가하거나 변경하는 계약에만 적용한다.

- `CURRENT`: 현재 구현을 확인한 계약
- `TARGET`: 다음 Prototype 구현에서 적용할 화면 계약
- `FOLLOW_UP`: 새 schema 또는 API가 필요한 후속 계약

frozen schema에 필드가 이미 존재하더라도 이 절에서 새로 정의하는 화면과 scope 규칙은
`TARGET`이다. 앞선 절의 기존 계약에는 상태 태그를 일괄 추가하지 않는다.

### [CURRENT] 유료 리포트 편집 품질

- prompt `v6`은 `프로젝트 운영 데이터 분석가이자 팀장 의사결정 보좌관`을 역할로 두고,
  팀장이 30초 안에 상황·결정·행동을 파악하는 유료 decision brief를 작성한다.
- 생성 순서는 `지배적인 변화 → 운영 영향 → 결정 → 행동`이다.
- headline은 숫자 없는 한 문장으로 가장 주목할 대상 하나를 지목한다.
- summary는 headline을 바꿔 말하지 않는다. 지배적 신호를 지목하고 그 뒤의 구체적 진단과
  지금 중요한 이유를 설명하며, 가능한 경우 서로 다른 서버 evidence key를 2개 이상 사용한다.
- 단순히 “흐름을 점검”, “진행 상황을 모니터링”, “우선순위를 검토”하라는 일반론은 허용하지
  않는다. 영향받는 신호, 결과, 다음 확인 조건이 함께 있어야 한다.
- 위험은 신호, 예상 운영 영향, 그리고 그 위험을 확인하거나 배제할 관찰을 함께 적는다.
- 행동은 중복되지 않는 최대 3개이며 담당자, 수행 내용, 확인 시점, 줄이려는 위험을 포함한다.
  확인 시점은 evidence placeholder이거나 `다음 주간 리뷰 전`처럼 명시적인 상대 구간이어야
  하며, 암시로 남기지 않는다.
- 리더 결정은 답할 수 있는 선택·승인 질문과 결정 지연의 영향을 포함한다.
- prompt 강화는 `SCHEMA_VERSION`을 바꾸지 않으므로 저장 마이그레이션이 없다. 기존 revision은
  frozen 결과를 유지한다. 강화된 prompt 결과를 받으려면 새 revision을 생성해야 하며, 기존
  확정본은 새 revision 확정 전까지 그대로 유지된다.

### [CURRENT] 웹 reader 표시 규칙

웹 reader는 같은 화면에 서버가 검증한 사실과 AI가 쓴 해석을 함께 싣는다. 독자가 둘을 혼동하지
않도록 다음 표시 규칙을 적용한다. 이 규칙은 화면 표현만 정의하며 frozen 값, 정렬, 투영 계약을
바꾸지 않는다.

- 모든 본문 블록은 출처 레일로 `사실`, `해석`, `결정·행동` 중 하나를 표시한다.
  레일은 각 블록의 제목이 이미 같은 뜻을 말하므로 `aria-hidden`으로 접근성 트리에서 제외한다.
- 수치·KPI·델타·evidence key·`TASK-`/`MEMBER-` 참조는 고정폭 서체로 표시해 서버 검증값임을
  드러낸다. 서술형 판단은 명조 계열로 표시한다.
- 지난주 대비 변화는 부호가 아니라 방향으로 읽는다. 완료 업무 감소와 지연 업무 증가는 부호가
  반대지만 둘 다 `악화`이며, 변화가 없으면 `변화 없음`으로 표시한다.
- 경고색은 위험·지연·악화에만 사용한다. 장식 목적의 강조색은 두지 않는다.
- 서로 다른 단위의 변화량을 하나의 막대 축에 함께 그리지 않는다.
- 우선 행동은 `P1 → P2 → P3` 한 열로 표시해 우선순위 읽기 순서를 유지한다.

### [TARGET] 웹 기본값과 scope

- 초기값은 `scope=GROUP`, `density=STANDARD`다.
- scope가 없거나 invalid면 `GROUP`을 사용한다.
- density가 없거나 invalid면 `STANDARD`를 사용한다.
- `INDIVIDUAL_MEMBER`에서 member ref가 없거나 frozen
  `operations.members[].member.ref`에 없으면 member ref를 제거하고 `GROUP`으로 복구한다.
  유효한 density는 유지하며, density도 invalid면 `STANDARD`를 사용한다.
- 유효한 frozen member가 담당 업무 0건이어도 `GROUP`으로 복구하지 않는다. 이 경우
  `선택된 기간에 담당 업무 없음`을 표시한다.
- `MEMBER_COMPARISON`에 member ref가 들어오면 무시하고 제거한다.
- member 선택과 표시는 frozen `member.ref`와 `label`만 사용한다. live membership이나 현재
  이름을 조회해 복구하지 않는다.
- 미할당 업무는 GROUP 위험과 근거에는 유지하지만 개인 scope와 팀원 예외 후보에서는 제외한다.

scope, density, member ref 변경은 동일 frozen response의 client-side 투영만 바꾼다. AI 재생성,
추가 report API 호출, live 업무 조회, revision 생성·변경, 저장 JSON 변경, PDF 입력·구성 변경을
발생시키지 않는다.

### [TARGET] 밀도별 섹션 표시표

`조건부`는 frozen 값이 있거나 기존 표시 조건을 만족할 때만 표시한다.

밀도는 단순히 같은 화면의 길이만 바꾸지 않고 사용 목적을 구분한다.

- `STANDARD`는 기본값이다. 팀장이 프로젝트의 과정, 결과, 진행 상황, 위험, 다음 행동을
  한눈에 판단하는 운영 리포트다.
- `SUMMARY`는 회의 자료다. 핵심 판단, 대표 위험, 합의가 필요한 결정 안건, 회의 후 실행
  항목을 남기고 일별·팀원별·업무별 상세표는 숨긴다.
- `DETAILED`는 상세보기다. 표준 내용에 일별 흐름, 팀원 흐름, 전체 frozen 업무와 근거
  drill-down을 추가해 검증과 후속 조치에 사용한다.

| 공통 TEAM 섹션 | `SUMMARY` | `STANDARD` | `DETAILED` |
|---|---:|---:|---:|
| 헤더·발행 상태·종합 상태·기간·확신·데이터 상태 | 표시 | 표시 | 표시 |
| PARTIAL history 경고 | 조건부 | 조건부 | 조건부 |
| 30초 리더 브리프 | 표시 | 표시 | 표시 |
| BASELINE·지난주 비교 | 표시 | 표시 | 표시 |
| KPI | 4개 | 7개 | 7개 |
| 서버 확인 위험 | 전체 | 전체 | 전체 |
| AI 위험 후보 | 대표 1개 | 전체 | 전체 |
| 성과 | 숨김 | 표시 | 표시 |
| 다음 주 우선 행동 | 우선순위 최대 3개 | 전체 | 전체 |
| 회의 결정 안건 | 최대 3개 | 전체 | 전체 |
| AI 분석 원문 | 숨김 | 숨김 | 표시 |
| 데이터 제한 | 조건부 | 조건부 | 조건부 |
| 운영 상세 | scope별 투영표 적용 | scope별 투영표 적용 | scope별 투영표 적용 |

웹 문서의 기본 읽기 순서는 `30초 리더 브리프 → 지난주 비교 → 우선 행동 → 팀장 결정 →
상세 운영 지표 → 위험과 근거 → 성과·분석`이다. AI 행동과 결정이 상세 KPI나 긴 위험 목록 아래에
묻히지 않아야 한다.

상단 TEAM 핵심 판단, 비교, TEAM KPI, 서버 위험, AI 위험, 행동과 결정은 scope와 무관하게 같은
frozen TEAM 값을 유지한다. 개인 KPI는 운영 상세에 추가되며 TEAM narrative를 대체하지 않는다.

### [TARGET] scope별 운영 상세 투영

| Scope | `SUMMARY` | `STANDARD` | `DETAILED` |
|---|---|---|---|
| `GROUP` | 결정적 팀원 예외 최대 3명 | 전체 frozen 팀원 요약 | 전체 팀원 요약과 전체 frozen 업무 |
| `MEMBER_COMPARISON` | 결정적 팀원 예외 최대 3명 | frozen 배열 순서의 전체 팀원 비교 | 전체 팀원 비교와 팀원별 matched risk 업무 |
| `INDIVIDUAL_MEMBER` | 선택 팀원 KPI와 위험 badge | 선택 팀원 KPI와 matched risk 업무 | 선택 팀원 KPI, 모든 담당 업무와 evidence |

- `MEMBER_COMPARISON` 행은 frozen `operations.members[]` 순서를 유지한다. 수치, 이름, ref로
  재정렬하지 않는다.
- `GROUP`과 `INDIVIDUAL_MEMBER`의 일반 업무는 frozen `operations.tasks[]` 순서를 유지한다.
- `MEMBER_COMPARISON`의 matched risk 업무도 `operations.tasks[]` 순서를 유지한 채 팀원별로
  필터링한다.
- 선택 팀원 KPI는 `assigned`, `active`, `completed`, `delayed`, `onTimeRatePercent` 순서다.
- 선택 팀원 위험 badge는 연결 위험 중 최고 severity를 사용한다. 동률이면 서버 code precedence를
  적용한다.
- 팀원 예외가 3명 미만이면 존재하는 후보만 표시하고, 후보가 없으면 예외 없음을 표시한다.
- `INDIVIDUAL_MEMBER`의 담당 업무에는
  `task.assignee.ref == selected member.ref`인 업무만 포함한다.
- live 조회나 제목·이름 기반 연결은 허용하지 않는다.

### [TARGET] KPI 밀도 계약

`SUMMARY`는 다음 네 TEAM KPI만 고정 순서로 표시한다.

1. `metrics.totalTasks`
2. `metrics.completionRatePercent`
3. `metrics.onTimeRatePercent`
4. `metrics.statuses.delayed`

`STANDARD`와 `DETAILED`는 위 네 항목에 다음 항목을 이어서 표시한다.

5. `metrics.averageCompletionHours`
6. `metrics.statuses.onHold`
7. `metrics.checklist`

빈 KPI를 다른 KPI로 대체하지 않고 기존 unavailable 또는 `NO_DATA` 표현을 사용한다.

### [TARGET] 결정적 선택·정렬

- `analysis.topActions[].priority`는 숫자 `1|2|3`이며 오름차순으로 표시한다.
- 저장 배열 index와 `priority == index + 1`이어야 한다.
- `SUMMARY` 대표 행동은 P1이다.
- AI 위험 대표는 severity `HIGH > MEDIUM > LOW` 순서로 선택하고, 동률이면 frozen 배열 index가
  빠른 항목을 사용한다.
- 서버 위험은 known code, severity `HIGH > MEDIUM > LOW`, code precedence
  `OVERDUE_PRESENT`, `ON_HOLD_PRESENT`, `HIGH_PRIORITY_PRESENT`, 같은 code의 frozen 배열
  index 순서로 정렬한다.
- unknown server code는 known code 뒤에 두고 unknown끼리는 frozen 배열 순서를 유지한다.
- `MEMBER_COMPARISON`의 행 정렬은 frozen 배열 순서를 사용한다.
- 팀원 성과 등급·점수·순위는 **서버가 동결 지표에서 결정적 규칙으로 계산**해 evidence로 발급한다
  (「팀원 성과 등급」절). AI는 등급을 계산하거나 바꾸지 않으며, 태도·능력·의욕을 추론하지 않는다.
  배정 업무가 없거나 산출 근거가 없는 팀원은 `NOT_RATED`이며 순위에서 제외한다.
- `member.ref`와 `task.ref`의 최종 정렬은 locale 정렬이 아닌 대소문자 구분 ordinal 오름차순을
  사용한다.

### [TARGET] 위험–업무–팀원 연결

task reference 우선순위는 다음과 같다.

1. 위험 항목에 `taskRefs`가 있으면 frozen `operations.tasks[].task.ref`와 일치하는 reference만
   연결한다.
2. 유효한 `taskRefs`가 하나라도 있으면 evidence key나 상태 규칙으로 업무를 추가하지 않는다.
3. `taskRefs`가 존재하지만 모두 invalid면 연결 업무 없음과 contract mismatch 또는 데이터 제한을
   표시하고 넓은 추론으로 복구하지 않는다.
4. `taskRefs`가 비어 있을 때만 위험 fallback registry를 적용한다.
5. live task 조회, 제목 검색, assignee 추측은 허용하지 않는다.

팀원 예외는 위 규칙으로 연결된 업무의 frozen assignee만 후보로 삼고 다음 순서로 최대 3명을
선택한다.

1. 최고 위험 severity
2. code precedence
3. 중복 제거된 연결 업무 수 내림차순
4. `operations.members[].delayed` 내림차순
5. `member.ref` 대소문자 구분 ordinal 오름차순

`assigned > 0 && completed == 0` 같은 추론은 사용하지 않는다.

지연 계약은 다음과 같다.

- 그룹: `metrics.statuses.delayed`
- 팀원: `operations.members[].delayed`
- 업무: `operations.tasks[].dueState == "OVERDUE"`
- 기간 마지막 날 종료 전에 due이고 frozen snapshot 상태가 non-terminal인 업무만 지연으로
  계산한다.

### [TARGET] 위험 fallback registry

| Server risk code | 필수 evidence key | Frozen task predicate |
|---|---|---|
| `OVERDUE_PRESENT` | `tasks.delayed` | `dueState == "OVERDUE"` |
| `ON_HOLD_PRESENT` | `tasks.onHold` | `status == "ON_HOLD"` |
| `HIGH_PRIORITY_PRESENT` | `tasks.highPriority` | `priority == "HIGH" || priority == "URGENT"` |

- `taskRefs`가 없을 때만 필수 evidence key와 frozen task predicate를 모두 적용한다.
- 필수 evidence key가 없으면 업무를 연결하지 않고 contract mismatch 또는 데이터 제한을 표시한다.
- 동일 `task.ref`가 여러 위험에 연결돼도 업무 수에는 한 번만 집계한다.
- 하나의 업무에는 연결 위험 중 최고 severity를 대표값으로 사용한다.
- severity와 code precedence가 같으면 frozen 위험 배열 index가 빠른 항목을 먼저 사용한다.
- unknown code에는 predicate를 추론하지 않는다.
- reference 정렬이 필요한 마지막 동률만 대소문자 구분 ordinal 오름차순을 사용한다.

### [CURRENT] 확정 AI 주간 리포트 표준 PDF

확정 AI 주간 리포트의 현재 표준 PDF는 `GROUP canonical PDF`다.

- `FINALIZED` report만 `/ai-weekly/{reportId}/pdf`로 다운로드한다.
- 입력은 `groupId`, `reportId`이며 scope, density, member 입력은 없다.
- 요약, 상태·기간·확신·발행 상태, BASELINE, 전체 업무·완료율·기한 준수율·지연 KPI, 서버 위험,
  AI 위험, 성과, 권고 행동, 리더 결정, 근거 업무, 데이터 제한을 포함한다.
- 웹 `DETAILED`와 동일한 문서로 규정하지 않는다.
- 일별 흐름, 팀원 표, 체크리스트 KPI, 평균 완료시간, `analysis.changes`는 현재 표준 PDF에
  포함하지 않는다.
- 기존 무료 기본 PDF의 계약, 권한, 한도, 실패 독립성은 변경하지 않는다.

### [FOLLOW_UP] 후속 계약

다음 항목은 이번 Spec 보강과 후속 Prototype 동작 구현에서 제외한다.

- scope·density 서버 저장
- member-scoped PDF
- 이전 revision 권고의 실행 결과 추적
- 숫자 건강점수
- 팀원 등급·순위·상대평가
- 새 schema가 필요한 정체·위험 추론
- 픽셀 단위 Prototype CSS·application shell 복제

## 개인정보와 OpenAI 전송 경계

Provider 전송 허용:

- 상태·개수·비율·기간·체크리스트 같은 집계
- 구조화 blocker 유형·다음 조치·검토 시점
- `TASK-`, `GOAL-`, `MEMBER-` 별칭
- 서버가 발급한 evidence key

Provider 전송 금지:

- 업무 제목·설명·댓글
- 이메일·닉네임
- 자유 입력 blocker 사유
- 로컬 reference index의 실제 제목·이름

실제 제목과 이름은 서버의 reference index에만 두고 권한 검사를 통과한 화면·PDF에서 결합한다.
`OPENAI_API_KEY`, provider 요청 payload, 응답 원문은 로그에 남기지 않는다. 로그에는 timeout,
거절, I/O, 계약 불일치 같은 결과 코드만 기록한다.

## 환경변수

| 변수 | 기본값 | 경계 |
|---|---|---|
| `AI_REPORT_ENABLED` | `false` | 운영·QA 승인 전 비활성화 |
| `OPENAI_API_KEY` | 빈 값 | 서버 전용 secret, `VITE_` 금지 |
| `OPENAI_MODEL` | `gpt-5.6-luna` | 환경별 승인 모델 |
| `OPENAI_REQUEST_TIMEOUT` | `90s` | SDK 요청 전체 `timeout(Duration)` |
| `AI_REPORT_GENERATION_LEASE` | `4m` | provider timeout보다 길게 설정 |

SDK에 없는 별도 connect/read timeout을 노출하지 않는다. 자동 재시도는 `maxRetries(0)`으로 유지하고,
애플리케이션의 revision·attempt 소유권으로 중복 호출과 stale worker를 통제한다.

## 실패 정책

- 기능 플래그 또는 키가 없으면 `AI_REPORT_NOT_CONFIGURED`다.
- timeout은 `AI_REPORT_TIMEOUT`, provider I/O·가용성 문제는 `AI_REPORT_PROVIDER_UNAVAILABLE`다.
- 거절·불완전·스키마/근거 불일치는 안전한 `AI_REPORT_*` 코드로 변환한다.
- provider 실패 시 실패 revision만 기록하고 기존 finalized revision은 유지한다.
- AI 실패는 기본 PDF 조회·다운로드를 막지 않는다.
- 자동 SDK 재시도를 하지 않으므로 한 요청의 중복 과금 가능성을 숨기지 않는다.
- 실제 OpenAI 품질과 오류 동작은 유효한 키를 사용하는 별도 승인 테스트 전까지 미검증으로 표시한다.

## DB와 보존

| 테이블 | 책임 | 주요 제약·인덱스 |
|---|---|---|
| `reports` | revision, 생성·공개 상태, frozen JSON, 모델·토큰·failure·lease | 그룹/유형/기간/언어/revision unique, 시리즈·공개 상태 인덱스 |
| `task_activity_events` | 보고서 재현용 불변 업무 snapshot | 그룹/시각, 업무/시각, 목표/시각 인덱스 |
| `weekly_objectives` | 그룹 주간 목표 최대 3개 | 그룹/주차/position unique |
| `task_weekly_objective_links` | 업무와 해당 주 목표 연결 | 업무/주차 unique |

`reports.source_report_id`는 재생성 계보를, `finalized_by_member_id`와 `finalized_at`은 공개 결정을
기록한다. 활동 이벤트와 리포트 revision에는 수정·삭제 API를 제공하지 않는다. 목표 삭제 시 현재 링크는
제거되지만 이미 동결된 리포트 근거를 다시 계산해 덮어쓰지 않는다.

## API

### 기본 PDF

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/groups/{groupId}/reports/basic.pdf` | `scope`, `periodType`, `from`, `to`, `language`로 PDF attachment 생성 |
| POST | `/api/v1/groups/{groupId}/reports/access` | 기존 클라이언트 호환용 권한·한도 확인. PDF 다운로드 자체가 아님 |

### AI 주간 리포트

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/v1/groups/{groupId}/reports/ai-weekly?weekStart=&language=` | LEADER 최신 리포트 조회 |
| POST | `/api/v1/groups/{groupId}/reports/ai-weekly` | `{weekStart,language}` 생성. 신규 `201`, 캐시 `200` |
| GET | `/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}` | LEADER 전체, MEMBER finalized 조회 |
| GET | `/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/pdf` | finalized PDF attachment |
| GET | `/api/v1/groups/{groupId}/reports/ai-weekly/revisions?weekStart=&language=` | LEADER revision 목록 |
| PATCH | `/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/draft` | `{expectedEditorVersion,content}` 편집 |
| POST | `/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/regenerations` | 새 revision 생성 |
| POST | `/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/finalization` | draft 확정 |

### 주간 목표

| Method | Path | 설명 |
|---|---|---|
| GET/POST | `/api/v1/groups/{groupId}/weekly-objectives` | 조회/LEADER 생성 |
| PATCH/DELETE | `/api/v1/weekly-objectives/{objectiveId}` | LEADER 수정/삭제 |
| GET/PUT | `/api/v1/tasks/{taskId}/weekly-objective` | 연결 조회/설정·해제 |

대표 계약 오류는 `AI_REPORT_PAID_REQUIRED`, `GROUP_LEADER_REQUIRED`, `AI_REPORT_WEEK_INCOMPLETE`,
`AI_REPORT_GENERATING`, `AI_REPORT_WEEKLY_LIMIT`, `AI_REPORT_NOT_FINALIZED`,
`AI_REPORT_EDITOR_VERSION_CONFLICT`, `REPORT_PDF_GENERATION_FAILED`다.

## 검증

이번 문서·계약 정합성 변경의 최소 자동 검증:

```powershell
cd backend
.\mvnw.cmd -q "-Dtest=TaskActivityMetricsSnapshotSourceTest,OpenAiResponsesNarrativeAdapterTest" test
cd ..
git diff --check origin/main
git diff --name-only origin/main
```

정합성 검색은 다음 세 명칭이 남아 있지 않아야 한다.

```powershell
rg -n "OPENAI_CONNECT_TIMEOUT|OPENAI_READ_TIMEOUT" `
  .env.example backend/src/main/resources/application.properties
rg -n "REASSIGN|\bWAIT\b" `
  backend/src/main/java/com/teamproject/task/application/TaskReportDataQuery.java
```

기능 출시 전 별도 승인 검증:

- 실제 download event, `%PDF-` header와 한국어 글꼴
- LEADER draft와 MEMBER finalized 권한
- BASELINE 표시와 frozen revision
- provider 실패 뒤 기본 PDF·기존 finalized 유지
- 유효한 API 키를 사용한 timeout·거절·응답 계약

Fake AI와 브라우저 fixture는 화면·캐시·계약 검증용이다. 실제 OpenAI 품질이나 provider 실패 동작의
증거로 사용하지 않는다.
