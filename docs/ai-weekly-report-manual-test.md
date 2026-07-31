# AI 주간 리포트 테스트 안내

AI 주간 리포트의 권한, 데이터 경계, 산출물과 검증 절차는
[AI 주간 리포트 계약](./spec/AiWeeklyReport.md#검증)을 기준으로 한다.

## 2주 MySQL fixture 실행

Docker Desktop을 실행한 뒤 Windows PowerShell에서 다음 명령으로 수동 테스트
환경을 실행한다. **수동 테스트의 기준 실행은 실제 provider(`-RealAi`)다.**

```powershell
.\scripts\ai-report-manual-test.ps1 start -RealAi
```

`-RealAi`는 저장소 루트 `.env`의 `OPENAI_API_KEY`를 읽어 `AI_REPORT_ENABLED=true`로
실행한다. 키가 없거나 비어 있으면 시작하지 않는다. 모델은 `OPENAI_MODEL`
(기본 `gpt-5.6-luna`), 타임아웃은 `OPENAI_REQUEST_TIMEOUT`(기본 `90s`)을 따른다.
**리포트를 생성할 때마다 실제 호출이 발생하고 과금된다.**

`-RealAi`를 붙이지 않은 `start`는 결정론적 Fake AI로 실행되며, 네트워크나 키 없이
화면·플로우만 훑어볼 때만 쓴다. 이 실행은 검증 증거가 되지 못한다(아래 [검증 경계](#검증-경계) 참고).

```powershell
.\scripts\ai-report-manual-test.ps1 start
```

두 실행 모두 `127.0.0.1:13307`의 전용 MySQL 8.4 컨테이너에 실제 데이터를 저장하고
백엔드와 프런트엔드를 함께 실행한다. 기존 `localhost:3306/teamProject` 개발 DB나
외부 DB에는 쓰지 않는다. 다른 DB가 연결되면 fixture가 데이터를 쓰기 전에 중단한다.

현재 날짜에 맞는 두 주 데이터를 처음부터 다시 만들려면 다음 명령을 사용한다.

```powershell
.\scripts\ai-report-manual-test.ps1 reset
```

실행 상태와 종료 명령은 다음과 같다.

```powershell
.\scripts\ai-report-manual-test.ps1 status
.\scripts\ai-report-manual-test.ps1 stop
```

`reset`과 `stop` 뒤의 재실행에도 `-RealAi`를 그대로 붙인다. 붙이지 않으면 Fake AI로
되돌아가며, 화면상으로는 구분되지 않는다. 실행 중인 모드는 `start` 출력의 `AI :` 줄로 확인한다.

`stop`은 전용 컨테이너와 저장 데이터를 제거한다. 이후 `start`를 실행하면 같은
구성의 최신 두 주 데이터를 다시 만든다.

- 기준 시간대: `Asia/Seoul`
- 리포트 주: 마지막으로 완료된 월요일~일요일, 업무 24건
- 비교 주: 리포트 주의 직전 월요일~일요일, 업무 18건
- 구성: PAID TEAM, 팀장 1명, 팀원 4명
- 포함 상태: 완료, 진행, 보류, 할 일, 승인 요청, 취소, 반려
- 포함 사례: 지연, 고우선순위, 미할당, 체크리스트, 업무 활동 이력

로그인 계정은 로컬 수동 테스트 전용이다.

```text
아이디: ai_report_tester
비밀번호: password123!
```

백엔드를 실행한 상태에서 프런트엔드를 `http://127.0.0.1:5174`로 열고 로그인한다.
`AI 주간 리포트 테스트팀`에서 마지막 완료 주를 선택하면 해당 주 리포트와 직전 주
BASELINE 비교를 함께 확인할 수 있다.

## 리포트 생성 절차 (2026-07-31 기준)

그룹 대시보드 아래 `업무 리포트` 영역에서 진행한다. `AI 리포트` 버튼은 **유료 팀의
팀장에게만** 보인다. 무료 그룹이나 일반 팀원에게는 렌더되지 않는다.

1. 연도·월·주차를 고른다. 기간 제약은 "이미 끝난 기간"뿐이다. 주간·월간·연간,
   달 기준 절단 주차 모두 받는다. 끝나지 않은 기간을 고르면 모달이 이유를 알린다.
2. `AI 리포트`를 누르면 언어 선택 모달이 열린다. `한국어 다운로드` / `English download`.
3. 그 기간·언어로 만들어 둔 리포트가 이미 있으면 재생성 여부를 먼저 묻는다.
   - `예, 새로 생성` — OpenAI를 다시 호출하고 리비전을 올린다. **과금된다.**
   - `아니요, 기존 리포트 받기` — 저장본을 그대로 내려받는다. 호출 0회.
4. 산출물은 **HTML 파일**이다. PDF가 아니다. 파일을 브라우저로 열고
   `Ctrl+P → PDF로 저장`한다.

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
provider 동작에 대한 검증 증거는 `-RealAi` 실행에서만 나온다. 검증 기록에는 실행 모드와
사용 모델을 함께 남긴다.

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
