# 프로젝트 설계 문서

로컬 알파에서 합의한 화면·권한·데이터·API 기준과 실제 구현 계약을 모은다.
환경별 설정과 단계 승급 기준은 [5단계 환경 승급 체계](./environments/README.md)를 기준으로 관리한다.
환경에 영향을 주는 기능 변경 기록은 [`changes/`](./changes/)에 날짜별로 추가한다.

## 디자인

- [사이트맵과 사용자 흐름](./design/SiteMapAndUserFlow.md)
- [PC·모바일 와이어프레임](./design/Wireframes.md)
- [디자인 시스템](./design/DesignSystem.md)

## 기술 명세

- [권한 매트릭스](./spec/PermissionMatrix.md)
- [초기 ERD](./spec/InitialERD.md)
- [API 계약](./spec/ApiContract.md)
- [AI 주간 리포트 계약](./spec/AiWeeklyReport.md)

문서의 상태는 `구현 기준`, `설계 기준`, `후속 단계`로 구분한다. 구현 중 계약을 바꾸면 해당 문서와 `devLog`를 같은 작업에서 갱신한다.
