# 1단계: 로컬 테스트 QA

## 목적

개발자 컴퓨터에서 기능, 권한, DB 마이그레이션, 프런트 빌드와 API 회귀 테스트를 가장 빠르게 확인한다.

## 환경

- Java 21(`java -version`과 `JAVA_HOME`이 모두 21이어야 한다), Node.js 20 이상(`.nvmrc` 기준), MySQL 8.x
- 프런트 `http://localhost:5174`
- 백엔드 `http://localhost:8081`
- 로컬 전용 `teamProject`와 테스트 전용 `teamProject_test`
- 실제 개인정보 대신 더미 데이터와 `@local.test` 이메일 사용

## 수정할 정보

| 항목 | 로컬 값 |
| --- | --- |
| `FRONTEND_URL` | `http://localhost:5174` |
| `VITE_PUBLIC_SITE_URL` | `http://localhost:5174` |
| `AUTH_SECURE_COOKIE` | `false` |
| `SERVER_FORWARD_HEADERS_STRATEGY` | 기본값 |
| `MAIL_ENABLED` | `false` |
| `TOSS_TEST_MODE` | `true` |
| `DEMO_ENABLED` | `true` |
| `VITE_ALLOW_INDEXING` | `false` |
| 업로드 | 프로젝트 외부로 노출되지 않는 로컬 `uploads` |
| 비밀값 | 개인 로컬 `.env`, 테스트 전용 키 |

## 필수 QA

- `frontend`: `npm ci`, `npm run build`
- `backend`: `./mvnw test` (Windows PowerShell은 `.\mvnw.cmd test`)
- 필요 시 `backend/scripts/test-mysql.sh`로 MySQL 마이그레이션 검증
- `backend/scripts/seed-demo-data.sh` 후 김팀장 데모 조회
- 권한 없는 그룹·업무·이미지 요청이 401/403/404로 거부되는지 확인
- 가입 필수 동의 누락이 거부되고 선택 동의가 각각 저장되는지 확인
- 결제 로그에 시크릿키, billing key, 카드정보가 남지 않는지 확인

## 금지 사항

- 실제 고객 이메일·전화번호·프로필 이미지 사용
- 운영 OAuth/SMTP/결제 시크릿 사용
- 개발 DB를 통합 테스트의 `create-drop` 대상으로 사용

## 2단계 진입 조건

- 전체 자동 테스트 성공
- 프런트 프로덕션 빌드 성공
- 외부 브라우저·모바일·OAuth·결제 콜백 등 HTTPS 검증 필요성이 명확함
- 로컬 `.env`와 로그에 운영 비밀값이 없음
