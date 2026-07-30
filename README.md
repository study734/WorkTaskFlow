# ToTaskFlow 로컬 MVP

로컬 MySQL을 기준으로 인증, 그룹·멤버십, 업무, 협업 알림, 캘린더와 대시보드를 제공하는 팀 프로젝트 MVP입니다.

개발 순서와 현재 단계는 [`DevFlow.md`](./DevFlow.md), 날짜별 작업·검증 기록은 [`devLog/`](./devLog/)에서 관리합니다.
사이트맵·와이어프레임·디자인 토큰·권한·ERD·API 기준은 [`docs/`](./docs/)에 있습니다.
로컬부터 실제 운영까지의 5단계 환경 승급은 [`docs/environments/`](./docs/environments/)에서 관리합니다.
결제 테스트와 비밀키 운영은 [`docs/payment/TossPaymentsIntegration.md`](./docs/payment/TossPaymentsIntegration.md),
이미지 저장·EC2 전환 기준은 [`docs/deployment/ImageStorage.md`](./docs/deployment/ImageStorage.md),
공개 사이트 구조와 검색 등록은 [`docs/deployment/SearchConsole.md`](./docs/deployment/SearchConsole.md)를 따릅니다.
Google 가입·로그인 설정은 [`docs/deployment/GoogleOAuth.md`](./docs/deployment/GoogleOAuth.md)를 따릅니다.

## 포함 기능

- 아이디/비밀번호 회원가입과 로그인
- 회원가입 이메일 인증(6자리 코드)
- 아이디 찾기(가입 이메일로 안내)
- 비밀번호 찾기(일회용 재설정 링크)
- JWT access token + 회전식 HttpOnly refresh token
- 로그아웃과 현재 사용자 조회
- Google OAuth 2.0 로그인과 별도 동의 후 신규 소셜 회원 생성
- React 인증 화면과 로그인 확인용 첫 화면
- 로컬 MySQL 기반 개발 환경(Docker 전환은 후속 인프라 단계)
- Flyway 기반 DB 스키마 버전 관리와 Hibernate 검증

아이디 찾기와 비밀번호 찾기는 계정 존재 여부를 API 응답으로 노출하지 않습니다. 같은 이메일의 일반 계정과 소셜 계정도 자동 연결하지 않으므로, 추후 로그인 상태에서 명시적인 `계정 연결` 기능을 추가해야 합니다.

SMTP 발송은 best-effort로 처리한다. 외부 메일 서버 장애가 발생해도 이메일 인증·비밀번호 재설정 토큰과 그룹 초대 저장은 롤백하지 않으며, 실패 로그에는 수신자를 마스킹하고 메일 본문과 토큰을 기록하지 않는다. 현재 로컬 MVP에는 자동 재시도·Outbox가 없으므로 사용자가 다시 요청해야 하며, 운영 메일 재시도는 후속 인프라 단계에서 추가한다.

## 백엔드 패키지 경계

```text
com.teamproject
├── TeamProjectApplication.java  # 최상위 Spring Boot 스캔 루트
├── user/                       # User 데이터와 계정 생명주기
├── authentication/             # 가입·로그인·복구·세션·OAuth
├── authorization/              # 공개/보호 API와 향후 역할 정책
├── group/                      # 개인·팀 그룹과 그룹별 멤버십
├── task/                       # 업무·상태 이력·체크리스트와 업무 API
├── comment/                    # 업무 댓글과 작성자 권한 API
├── jwt/                        # Access JWT 발급·검증·Bearer 필터
└── common/                     # 공통 예외·API 오류·헬스체크
```

Refresh Token은 JWT가 아닌 난수 기반 세션 토큰이므로 `authentication/domain/token` 영역에 둡니다. 패키지 정리 후에도 기존 `/api/v1/auth/**`, DB 테이블, JWT Claim과 Refresh Cookie 계약은 그대로 유지합니다.

## 프런트엔드 경계

```text
src
├── app/             # 라우팅과 홈 조합
├── api/             # HTTP client와 인증·사용자·그룹·업무 API
├── features/auth/   # 로그인·가입·복구·OAuth 화면과 인증 UI
├── features/group/  # 그룹 목록·생성 UI
├── features/task/   # 업무 등록·목록·상세 UI
├── features/user/   # 프로필·계정 설정 화면
└── main.tsx         # 애플리케이션 진입점
```

의존 방향은 `app → features → api`이며, API 계층은 화면이나 라우팅을 참조하지 않습니다.

## 로컬 실행

필요 환경: Java 21, Node.js 20 이상, 로컬 MySQL 8.x (Maven은 Wrapper가 자동으로 준비)

팀원 간 결과를 같게 유지하기 위해 아래를 먼저 맞춥니다.

- Java: `java -version`과 `JAVA_HOME`이 모두 21이어야 합니다. 기본 JDK가 17인 PC에서는 Wrapper가 release 21 컴파일에 실패하므로 실행 전 `JAVA_HOME`을 JDK 21로 지정합니다.
- Node: 저장소 루트의 `.nvmrc`가 기준이며 `nvm use`로 맞춥니다. `frontend/package.json`의 `engines`가 20 미만을 걸러냅니다.
- 줄바꿈·들여쓰기: `.gitattributes`와 `.editorconfig`가 기준입니다. 편집기에 EditorConfig 플러그인을 활성화합니다.
- 쉘: 아래 예시는 bash 기준입니다. Windows PowerShell에서는 `./mvnw`를 `.\mvnw.cmd`로 바꾸고, `.sh` 스크립트는 Git Bash에서 실행합니다.
- DB: 로컬에 설치한 MySQL 8.x가 기준입니다. `docker-compose.yml`은 MySQL을 직접 설치하지 않을 때 쓰는 선택지이며, 컨테이너를 쓰더라도 접속 정보는 아래와 같은 `localhost:3306/teamProject`로 맞춥니다.

```bash
cp .env.example .env
# .env의 SPRING_DATASOURCE_PASSWORD와 JWT_SECRET을 로컬 값으로 변경
# MySQL에서 teamProject 데이터베이스를 먼저 생성
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS teamProject CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

cd backend
./mvnw spring-boot:run
```

다른 터미널에서:

```bash
cd frontend
npm ci
npm run dev
```

개발 서버에서는 캐시 간섭을 피하기 위해 Service Worker를 등록하지 않는다. PWA 설치·업데이트·오프라인 안내는 배포용 로컬 빌드로 확인한다.

```bash
cd frontend
npm run build
npm run preview
```

`http://localhost:5174`를 연 뒤 브라우저의 앱 설치 메뉴를 확인한다. 앱 셸 정적 파일만 캐시하며 `/api` 응답은 사용자 데이터 보호와 최신성 유지를 위해 캐시하지 않는다. 따라서 오프라인에서는 저장된 화면과 연결 안내만 볼 수 있고 조회·등록·수정은 온라인 복귀 후 사용한다. Service Worker 동작을 처음부터 다시 확인하려면 브라우저 개발자 도구에서 해당 사이트 데이터를 삭제한 뒤 새로고침한다.

- 프런트엔드: http://localhost:5174
- 백엔드: http://localhost:8081
- 헬스 체크: http://localhost:8081/api/v1/health

루트의 `.env`를 Spring Boot와 Vite가 함께 사용합니다. 새 환경에서는 추적되는 `.env.example`을 `.env`로 복사하고 로컬 DB 비밀번호와 32자 이상의 임의 JWT secret을 설정합니다. 실제 `.env`는 Git에 포함하지 않습니다. 개발 기준 DB는 `localhost:3306/teamProject`의 로컬 MySQL이며, Docker Compose는 기능 개발 완료 후 인프라 정리 단계에서 다시 맞춥니다. 이메일 발송은 꺼져 있어서 인증번호와 비밀번호 재설정·그룹 초대 링크가 백엔드 로그의 `[LOCAL MAIL]`에 출력됩니다. 그룹 초대 링크의 기본 만료 시간은 72시간이며 `GROUP_INVITATION_HOURS`로 변경할 수 있습니다.

<!-- NGROK -->
### ngrok 테스트

백엔드와 프런트엔드를 평소처럼 실행한 뒤 프런트엔드 포트 하나만 외부에 연결합니다.

```bash
ngrok http 5174
```

ngrok이 출력한 HTTPS 주소가 `https://example.ngrok-free.app`이라면 루트 `.env`를 아래처럼 바꾸고 백엔드를 재시작합니다.

```properties
FRONTEND_URL=https://example.ngrok-free.app
AUTH_SECURE_COOKIE=true
SERVER_FORWARD_HEADERS_STRATEGY=framework
```

API와 소셜 로그인 요청은 프런트엔드 개발 서버가 로컬 백엔드 `8081` 포트로 전달하므로 백엔드용 ngrok 터널은 만들지 않습니다. 무료 도메인이 바뀌면 `FRONTEND_URL`도 다시 바꿔야 합니다.

소셜 로그인을 테스트할 때는 각 제공자의 개발자 콘솔에도 다음 HTTPS Redirect URI를 등록합니다.

- Google: `https://example.ngrok-free.app/login/oauth2/code/google`
- Kakao: `https://example.ngrok-free.app/login/oauth2/code/kakao`

ngrok 테스트를 끝내고 HTTP localhost로 돌아올 때는 `.env`의 `FRONTEND_URL=http://localhost:5174`, `AUTH_SECURE_COOKIE=false`로 복원하고 백엔드를 재시작합니다.
<!-- NGROK -->

### 로컬 시연 데이터

로컬 `teamProject` DB에 재현 가능한 시연 계정과 협업 데이터를 만들거나 초기 상태로 되돌리려면 다음 명령을 실행합니다.

```bash
cd backend
./scripts/seed-demo-data.sh
```

계정, 공통 비밀번호와 권장 발표 순서는 [로컬 알파 시연 시나리오](./docs/qa/DemoScenario.md)를 참고합니다. 스크립트는 로컬 개발 DB만 허용하며 일반 사용자 데이터는 삭제하지 않습니다.

## `.env` 설정

`.env.example`은 로컬 실행용 안전한 기본 형식을 제공하며 `change-me`와 `replace-with-...` 값은 실제 로컬 값으로 바꿔야 합니다. 실제 SMTP 발송 시 아래 값도 변경합니다.

```properties
MAIL_ENABLED=true
MAIL_SMTP_AUTH=true
MAIL_STARTTLS=true
MAIL_USERNAME=your-account@gmail.com
MAIL_PASSWORD=your-app-password
MAIL_FROM=your-account@gmail.com
```

Gmail은 일반 비밀번호가 아니라 앱 비밀번호를 사용하세요.

## DB 마이그레이션

인증 스키마의 기준선은 V1, 사용자 프로필·탈퇴는 V2~V3, 그룹·초대는 V4~V5, 업무·체크리스트·댓글·멘션은 V6~V9, 알림은 V10, 캘린더는 V11부터 확장하고 Google 동의 전 가입 요청은 V22에서 관리한다. V4는 기존 사용자의 PERSONAL 그룹도 보충한다. 새 로컬 MySQL DB에서는 Flyway가 순서대로 스키마를 만든 뒤 Hibernate가 엔티티와 스키마를 `validate`한다.

업무는 그룹 상세의 `업무 목록·등록`에서 테스트할 수 있다. PERSONAL 그룹 업무는 생성 즉시 `TODO`이면서 요청자가 담당자가 되고, TEAM 그룹 업무는 담당자 없이 `REQUESTED`로 시작한다. 업무 상세에서는 권한이 있는 사용자가 내용과 마감일을 수정하고, 팀장이 승인·반려·담당자 지정을 하며 담당자가 시작·보류·재개·완료할 수 있다. 상태 이력은 시간순으로 표시된다. 체크리스트는 모든 활성 멤버가 조회하고 담당자 또는 팀장이 미종료 업무에서 추가·수정·완료·삭제할 수 있다. 댓글은 모든 활성 멤버가 업무 상태와 관계없이 작성하고 작성자만 수정·소프트 삭제할 수 있으며, 같은 그룹의 활성 멤버를 선택해 멘션할 수 있다.

기존에 Hibernate `update`로 이미 테이블을 만든 로컬 DB만 최초 1회 `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true`로 실행해 현재 스키마를 version 1로 등록한다. 현재 개발 DB는 등록이 완료됐고 애플리케이션 기본값도 다시 `false`이므로 계속 끈 상태로 사용한다.

앞으로 DB 변경은 `V2__...sql`, `V3__...sql`처럼 새 migration을 추가한다. 이미 공유되거나 실행된 migration은 수정하지 않는다.

MySQL 통합 테스트는 개발 DB를 보호하기 위해 반드시 전용 `teamProject_test` DB에서 실행한다. 아래 스크립트가 DB를 만들고 테스트 URL을 분리한다. 테스트 설정은 `create-drop`이므로 루트 `.env`의 `teamProject` URL을 직접 `-Dspring.datasource.url`로 넘기면 안 된다.

```bash
cd backend
./scripts/test-mysql.sh
```

## 소셜 로그인 설정

`.env.example`의 자리표시자를 실제 비밀 저장소의 값으로 교체하면 로그인 화면의 Google 버튼이 활성화됩니다.

```properties
OAUTH2_GOOGLE_CLIENT_ID=실제_클라이언트_ID
OAUTH2_GOOGLE_CLIENT_SECRET=실제_클라이언트_보안_비밀
```

개발자 콘솔의 Redirect URI:

- Google: `http://localhost:5174/login/oauth2/code/google`

신규 Google 사용자는 인증 직후 계정을 만들지 않고 별도 동의 화면을 거칩니다. 운영 배포 시 `https://totaskflow.com/login/oauth2/code/google`을 정확히 등록하고 `FRONTEND_URL`, `AUTH_SECURE_COOKIE=true`, 강한 `JWT_SECRET`을 반드시 설정하세요. 전체 설정은 [`docs/deployment/GoogleOAuth.md`](./docs/deployment/GoogleOAuth.md)를 참고하세요.

## 후속 인프라 단계

Docker, AWS, S3, CI/CD는 로컬 기능 개발과 QA가 끝난 뒤 한 번에 구성한다. 아래 값은 현재 적용 대상이 아니라 후속 배포 단계의 참고 기준이다.

### AWS EC2 배포 시 변경할 값

EC2에서도 프로젝트 루트에 있는 `.env`를 사용합니다. 최소한 아래 항목은 운영 환경에 맞게 변경하세요.

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://운영-DB-주소:3306/teamProject?serverTimezone=Asia/Seoul
SPRING_DATASOURCE_USERNAME=운영_DB_사용자
SPRING_DATASOURCE_PASSWORD=강한_DB_비밀번호
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_FLYWAY_BASELINE_ON_MIGRATE=false
JWT_SECRET=충분히_길고_무작위인_운영용_비밀키
FRONTEND_URL=https://서비스-도메인
VITE_API_BASE_URL=/api/v1
AUTH_SECURE_COOKIE=true
```

백엔드 빌드와 실행은 Maven 기준입니다.

```bash
cd backend
./mvnw clean package
java -jar target/auth-api-0.0.1-SNAPSHOT.jar
```

`.env`에는 비밀번호와 비밀키가 들어가므로 `.gitignore`에서 계속 제외합니다. EC2에는 Git으로 올리지 말고 서버에서 직접 생성하거나 AWS Systems Manager Parameter Store/Secrets Manager로 관리하세요.

## 주요 API

| Method | Path | 기능 |
|---|---|---|
| POST | `/api/v1/auth/email-verifications` | 가입 인증번호 발송 |
| POST | `/api/v1/auth/email-verifications/confirm` | 인증번호 확인 |
| POST | `/api/v1/auth/signup` | 회원가입 |
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/refresh` | access token 갱신 |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| POST | `/api/v1/auth/username-reminders` | 아이디 안내 메일 |
| POST | `/api/v1/auth/password-resets` | 재설정 링크 메일 |
| POST | `/api/v1/auth/password-resets/confirm` | 새 비밀번호 저장 |
| GET | `/api/v1/auth/providers` | 활성화된 소셜 제공자 |
| GET | `/api/v1/auth/me` | 현재 사용자 |
| GET | `/api/v1/users/me` | 내 프로필 조회 |
| PATCH | `/api/v1/users/me` | 내 프로필 수정 |
| PUT | `/api/v1/users/me/password` | 비밀번호 변경·refresh token 전체 폐기 |
| DELETE | `/api/v1/users/me` | 재인증 후 탈퇴·개인정보 익명화 |
| GET | `/api/v1/groups` | 내 활성 그룹과 그룹별 역할 조회 |
| POST | `/api/v1/groups` | TEAM 그룹 생성·생성자 LEADER 등록 |
| GET | `/api/v1/groups/{groupId}` | 활성 멤버의 그룹 상세 조회 |
| PATCH | `/api/v1/groups/{groupId}` | LEADER의 그룹 설정 수정 |
| GET | `/api/v1/groups/{groupId}/members` | 활성 멤버의 멤버 공개 프로필 목록 |
| POST | `/api/v1/groups/{groupId}/invitations` | LEADER의 이메일 초대 생성 |
| GET | `/api/v1/groups/{groupId}/invitations` | LEADER의 초대 목록 조회 |
| DELETE | `/api/v1/groups/{groupId}/invitations/{invitationId}` | LEADER의 대기 초대 취소 |
| POST | `/api/v1/group-invitations/{token}/accept` | 초대 이메일 계정의 초대 수락 |
| PATCH | `/api/v1/groups/{groupId}/members/{memberId}/role` | LEADER/MEMBER 역할 변경 |
| DELETE | `/api/v1/groups/{groupId}/members/{memberId}` | LEADER의 멤버 내보내기 |
| DELETE | `/api/v1/groups/{groupId}/members/me` | 본인 그룹 탈퇴 |
| GET | `/api/v1/groups/{groupId}/tasks` | 활성 멤버의 그룹 업무 목록 |
| POST | `/api/v1/groups/{groupId}/tasks` | 활성 멤버의 업무 등록·제안 |
| GET | `/api/v1/tasks/{taskId}` | 활성 그룹 멤버의 업무 상세 |
| PATCH | `/api/v1/tasks/{taskId}` | 요청자·LEADER 또는 개인 업무 소유자의 내용 수정 |
| POST | `/api/v1/tasks/{taskId}/transitions` | 권한·현재 상태에 따른 업무 상태 변경 |
| PUT | `/api/v1/tasks/{taskId}/assignee` | LEADER의 활성 멤버 담당자 지정 |
| GET | `/api/v1/tasks/{taskId}/histories` | 활성 그룹 멤버의 상태 이력 조회 |
| GET | `/api/v1/tasks/{taskId}/checklist-items` | 활성 그룹 멤버의 체크리스트·진행률 조회 |
| POST | `/api/v1/tasks/{taskId}/checklist-items` | 담당자·LEADER의 체크리스트 항목 생성 |
| PATCH | `/api/v1/checklist-items/{itemId}` | 담당자·LEADER의 항목 내용·완료·순서 변경 |
| DELETE | `/api/v1/checklist-items/{itemId}` | 담당자·LEADER의 버전 확인 후 항목 삭제 |
| GET | `/api/v1/tasks/{taskId}/comments` | 활성 그룹 멤버의 댓글 조회 |
| POST | `/api/v1/tasks/{taskId}/comments` | 활성 그룹 멤버의 댓글 작성 |
| PATCH | `/api/v1/comments/{commentId}` | 작성자의 댓글 수정 |
| DELETE | `/api/v1/comments/{commentId}` | 작성자의 버전 확인 후 댓글 소프트 삭제 |
| GET | `/api/v1/notifications` | 내 알림 최신순 커서 페이지와 읽지 않음 개수 조회 |
| PATCH | `/api/v1/notifications/{notificationId}/read` | 수신자 본인의 단일 알림 읽음 처리 |
| PATCH | `/api/v1/notifications/read-all` | 내 미확인 알림 전체 읽음 처리 |
| GET | `/api/v1/calendars/events` | 개인·그룹 일정과 업무 마감 통합 기간 조회 |
| POST | `/api/v1/groups/{groupId}/calendar-events` | LEADER의 그룹 현지 시각 일정 등록 |
| PATCH | `/api/v1/calendar-events/{eventId}` | LEADER의 버전 확인 일정 수정 |
| DELETE | `/api/v1/calendar-events/{eventId}` | LEADER의 버전 확인 일정 삭제 |
| GET | `/api/v1/dashboard/me` | 활성 그룹의 내 담당 업무·일정·알림 통합 대시보드 |
| GET | `/api/v1/groups/{groupId}/dashboard` | 공개 범위가 허용된 멤버의 그룹 지표 조회 |

로컬 브라우저 검증 순서는 [`docs/qa/LocalAlphaChecklist.md`](./docs/qa/LocalAlphaChecklist.md)를 따릅니다.

## 다음 개발 기준

이 폴더를 새 Git 저장소로 분리한 뒤 팀 도메인 기능을 추가하면 됩니다. 배포 전에는 이메일 템플릿, 로그인/재설정 요청 IP 제한, 감사 로그, DB 마이그레이션, CI, 비밀키 저장소를 팀 운영 환경에 맞게 보강하세요.
