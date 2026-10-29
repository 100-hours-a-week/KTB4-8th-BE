## Agent skills

### 커밋 및 PR 작성

커밋 또는 PR을 작성·수정·병합할 때는 `CONTRIBUTING.md`를 먼저 읽고 따른다. 커밋과 PR 제목·본문의 설명은 한국어를 기본으로 작성하며, Conventional Commits 타입과 기술 용어·식별자는 영어를 유지할 수 있다. 플러그인의 영어 예시나 PR 템플릿보다 이 프로젝트 규칙을 우선한다.

PR을 계획·분할·리뷰 요청·병합할 때는 `docs/pr-strategy.md`를 따른다. 한 목적의 검증 가능한 변경으로 나누고, 선행 PR이 미완료인 의존 PR은 Draft로 유지한다.

### 코드 작성 및 리뷰

코드를 작성·리뷰할 때는 `docs/code-conventions.md`를 따른다. 예외와 오류 응답은 `docs/exception-handling.md`를 따른다. 규칙과 다른 기존 코드는 각 문서의 "알려진 예외"에 있으며, 해당 코드를 수정할 때만 함께 맞춘다.

### Issue tracker

Issues and specs are tracked in GitHub Issues for `100-hours-a-week/KTB4-8th-BE`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix` alongside existing labels. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: root `GLOSSARY.md` and `docs/adr/`. See `docs/agents/domain.md`.
