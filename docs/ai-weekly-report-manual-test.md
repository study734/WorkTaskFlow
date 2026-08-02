# AI 주간 리포트 테스트 안내

AI 주간 리포트의 권한, 데이터 경계, 산출물과 검증 절차는
[AI 주간 리포트 계약](./spec/AiWeeklyReport.md#검증)을 기준으로 한다.

## 상황별 fixture 실행

전용 컨테이너를 띄우던 `scripts/ai-report-manual-test.ps1`은 은퇴했다. 그 스크립트가
띄우던 시더 클래스는 legacy 정리(`e8d1199`) 때 계약과 함께 사라졌고, 되살려도 그룹
하나짜리 데이터라 "위험이 없는 기간"이나 "업무가 100건을 넘는 기간" 같은 상황을 볼 수
없었다. 지금은 평소 개발 DB에 상황별 그룹을 넣고 확인한다.

1. 개발 DB와 앱을 평소대로 띄운다. `.env`에 `AI_REPORT_ENABLED=true`와
   `OPENAI_API_KEY`가 있어야 실제 호출 경로가 열린다. 모델은 `OPENAI_MODEL`
   (기본 `gpt-5.6-luna`)을 따른다. **리포트를 생성할 때마다 과금된다.**

```bash
docker compose up -d mysql
```

2. 상황 fixture를 적재한다. 이름(`FIXTURE %`)으로 지우고 새로 만들기 때문에 몇 번을
   실행해도 같은 상태가 된다. 기간은 `2026-07-20 ~ 2026-07-27`로 고정이다.

```bash
docker exec -i worktaskflow-mysql-1 mysql -uroot -p<암호> teamProject < scripts/ai-report-fixture-matrix.sql
```

여섯 그룹이 생긴다. 모두 PAID TEAM이고 리더는 개발 계정 `devuser`다.

| 그룹 | 보려는 것 |
|---|---|
| FIXTURE 1 위험 없음 | 3페이지의 "검사 항목 요약"과 위험 0건 문체 |
| FIXTURE 2 지연 다수 | 지연 위험 후보와 4페이지 결정 |
| FIXTURE 3 승인 대기 | 담당자 없는 요청이 쌓였을 때 |
| FIXTURE 4 부하 편중 | 한 사람 집중과 재배분 권고 |
| FIXTURE 5 대량 업무 | 105건. 집계 모수와 분석 대상 잘림 공개 |
| FIXTURE 6 업무 없음 | 업무 0건 문서 전체 |

3. 프런트엔드를 `http://127.0.0.1:5174`로 열고 `devuser`로 로그인한 뒤, 각 그룹
   대시보드에서 2026년 7월 3주차를 골라 생성한다. 같은 주간이라 여섯 장을 나란히 놓고
   비교할 수 있다.

DB를 거치지 않고 API로 바로 뽑아 문서만 확인할 수도 있다.

```bash
curl -s -X POST "http://127.0.0.1:8081/api/v1/groups/<groupId>/reports/ai-weekly" -H "Content-Type: application/json" -H "Origin: http://localhost:5174" -H "Authorization: Bearer <accessToken>" -d '{"from":"2026-07-20","toExclusive":"2026-07-27","language":"KO","regenerate":false}'
```

`Origin` 헤더는 `.env`의 `FRONTEND_URL`과 정확히 같아야 한다. 다르면 CORS 단계에서
403이 나고 인증까지 가지 못한다. `regenerate=false`는 저장본이 있으면 재사용하므로
호출이 발생하지 않는다.

## 리포트 생성 절차 (2026-07-31 기준)

그룹 대시보드 아래 `업무 리포트` 영역에서 진행한다. `AI 리포트` 버튼은 **유료 팀의
팀장에게만** 보인다. 무료 그룹이나 일반 팀원에게는 렌더되지 않는다.

1. 연도·월·주차를 고른다. 주차와 기간은 서로 모순될 수 없다.
   - `월 전체`를 두면 기간은 `월간`·`연간`만 고를 수 있다(`주간` 비활성).
   - `N주차`를 고르면 기간이 `주간`으로 따라 옮겨진다(`월간` 비활성).
   - `연간`을 고르면 주차가 `월 전체`로 되돌아간다.

   기간 제약은 "이미 끝난 기간"뿐이다. 주간·월간·연간, 달 기준 절단 주차 모두 받는다.
   끝나지 않은 기간을 고르면 버튼에 이유가 뜬다. 진행 중인 달·해는 그래서 생성할 수 없고,
   지난 달·지난 주차·지난 해를 고르면 바로 눌린다.
2. `AI 리포트`를 누르면 언어 선택 모달이 열린다. `한국어 다운로드` / `English download`.
3. 그 기간·언어로 만들어 둔 리포트가 이미 있으면 재생성 여부를 먼저 묻는다.
   - `예, 새로 생성` — OpenAI를 다시 호출하고 리비전을 올린다. **과금된다.**
   - `아니요, 기존 리포트 받기` — 저장본을 그대로 내려받는다. 호출 0회.
4. 산출물은 **HTML 파일**이다. PDF가 아니다. 파일을 브라우저로 열고
   `Ctrl+P → PDF로 저장`한다.
   파일명과 문서 제목은 실제 기간을 따른다 — `toesa-ai-weekly-` / `-monthly-` / `-yearly-`,
   그리고 어느 단위에도 안 맞는 구간(달 기준 5주차 등)은 `-period-`다.

서버 PDF 렌더러는 CSS 2.1만 이해해 이 문서의 grid 레이아웃을 그리지 못한다.
기본 리포트와 같은 방식으로 완성된 HTML을 내려주고 인쇄는 브라우저에 맡긴다.

### 인쇄 설정

A4 4장으로 끊기려면 다음 세 가지를 맞춘다.

- 용지 **A4**
- 배율 **100%** (줄이면 4장이 3장으로 합쳐진다)
- 머리글·바닥글 **끄기**

배경 그래픽은 켜지 않아도 된다. 문서가 `print-color-adjust: exact`로 색을 고정한다.

## 검증 경계

Fake AI 또는 격리된 브라우저 fixture 통과를 실제 OpenAI provider 동작 검증으로 기록하지 않는다.
provider 동작에 대한 검증 증거는 `AI_REPORT_ENABLED=true`로 키를 넣고 실제 호출이 일어난
실행에서만 나온다. 검증 기록에는 실행 모드와 사용 모델을 함께 남긴다.

### 실제 호출 여부 확인법

화면만 봐서는 구분되지 않으므로 아래 중 하나로 확인한다.

- 리포트 문서 머리말의 `v7-2 · R{n} · 정상`(OpenAI) / `· 서버 기본 분석`(fallback)
- 저장 행의 `analysis_mode` 컬럼

```sql
SELECT id, language, revision, analysis_mode, model, generated_at
FROM ai_weekly_report_revision ORDER BY id DESC LIMIT 5;
```

- 생성 요청 소요 시간. 실제 호출은 수 초가 걸리고, 저장본 재사용은 수십 ms다.
- fallback으로 내려갔다면 백엔드 로그에 사유가 남는다.

```text
WARN  AiWeeklyReportGenerationService : AI weekly report analysis rejected,
      using server fallback: groupId=.. period=..  errors=[..]
```

`analysis_mode=SERVER_FALLBACK`인 결과는 OpenAI 동작 검증 증거가 아니다.
