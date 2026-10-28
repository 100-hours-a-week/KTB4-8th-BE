# PR 전략

이 문서는 PR의 범위·리뷰·병합을 정한다. 제목·본문의 한국어 및 Conventional Commits 형식은 [기여 규칙](../CONTRIBUTING.md)을 따른다. 아래 기본값과 예외는 프로젝트 정책이며 GitHub 설정이 자동으로 변경됐다는 뜻은 아니다.

## 한 PR은 한 목적

독립적으로 설명·검증·되돌릴 수 있는 변경을 한 PR로 만든다. 기능과 그 검증은 함께 담되, 독립적인 작성 규칙·기능 구현·기존 테스트 삭제를 묶지 않는다. 커밋 분리만으로 PR 분할을 대신하지 않는다.

줄 수를 일률적으로 제한하지 않는다. 스키마 snapshot과 대량 삭제는 실행 로직과 검토 부담이 다르다. 크다면 먼저 분할을 검토하고, 나눌 수 없는 관련 코드·테스트·설정은 함께 두면서 이유와 검토 순서를 적는다. 별도 병합된 상태에서도 관련 빌드와 동작이 유지돼야 한다. 근거: [Google의 작은 CL 지침](https://github.com/google/eng-practices/blob/master/review/developer/small-cls.md).

## 브랜치와 의존성

- 독립 PR은 최신 `origin/develop`에서 새 작업 브랜치를 만든다. 브랜치 이름은 `docs/pr-conventions`, `tests/legacy-tests`, `tests/223-mysql-migrations`처럼 목적을 드러낸다.
- 실제 선행 변경이 필요할 때만 부모 브랜치 위에 하위 브랜치를 만들고, 하위 PR의 base를 부모 브랜치로 둔다. 무관한 PR을 기다리도록 연결하지 않는다.
- 본문에 기준 브랜치·선행 PR 링크·남은 조건·병합 순서를 적는다. 선행 PR이 병합되지 않은 하위 PR은 Draft로 유지한다.
- 작업 브랜치를 base로 둔 PR은 기본 브랜치 대상 CI 필터에 걸리지 않을 수 있다. 검사 없음은 통과가 아니다. 로컬 검증을 남기고 base 전환·리뷰 준비 후 관련 CI 실행을 확인한다.
- 부모가 병합되면 하위 PR을 갱신·재검증하고 `develop`을 대상으로 바꾼 뒤 리뷰를 요청한다. 미완료 부모를 포함한 하위 브랜치를 먼저 병합해 개별 검증을 우회하지 않는다.

의존 변경을 쌓아 검토 대기를 줄일 수 있지만 각 변경은 검증 가능한 단위여야 한다. 근거: [Google의 의존 CL 분할](https://github.com/google/eng-practices/blob/master/review/developer/small-cls.md).

## 리뷰 요청 전

1. 제목과 설명에서 해결할 문제·결과·제외 범위를 밝힌다. 넓은 diff에는 읽을 순서와 특히 판단받을 부분을 적는다.
2. 작성자가 diff를 먼저 검토하고 무관한 파일·로그·키·로컬 설정이 없는지 확인한다. 워크플로·인증·권한·의존성 변경은 보안 영향도 확인한다.
3. 해당 커밋·PR base에 대해 실행한 검사와 전후 근거를 남긴다. 관련 CI 실패·미해결 리뷰·충돌·선행 의존성이 남으면 해결하거나 Draft로 상태와 남은 조건을 드러낸다. 기존 실패를 숨기거나 테스트 삭제를 버그 수정으로 표현하지 않는다.
4. 준비가 끝나면 Draft를 해제하고 리뷰를 요청한다. 상태 표시에는 Draft와 필요한 기존 라벨을 사용한다.

명확한 설명·자체 검토·이슈 연결은 리뷰 맥락을 전달하기 위한 것이다. 근거: [GitHub의 리뷰 지원 지침](https://docs.github.com/en/pull-requests/concepts/helping-others-review-your-changes).

## 병합 방식

| 방식 | 프로젝트 사용 기준 | 이력상 결과 |
|---|---|---|
| Squash and merge | 짧은 작업 브랜치의 한 목적 PR에 기본 사용 | base에 최종 커밋 하나를 남김 |
| Merge commit | develop→main 등 장기 브랜치의 통합·릴리스 경계와 전체 이력 보존이 필요한 경우 | 개별 커밋과 병합 지점을 남김 |
| Rebase and merge | 개별 커밋을 보존해야 하며 모두 독립적·검증 가능한 경우에 사유를 적고 사용 | 병합 커밋 없이 개별 커밋을 남기며 SHA가 바뀜 |

GitHub의 병합 방식별 차이를 바탕으로 정한 정책이다. squash는 중간 커밋이 base에 따로 남지 않고, GitHub rebase는 새 SHA를 만든다. 근거: [GitHub PR 병합 방식](https://docs.github.com/en/pull-requests/reference/pull-request-merges).

병합 담당자는 최종 제목·본문을 한국어 규칙에 맞게 확인하고 필요한 리뷰·관련 CI·충돌 상태를 확인한다. squash 기본 메시지가 커밋 목록으로 채워져도 그대로 제출하지 않는다. merge commit도 자동 영어 제목을 한국어로 편집한다.

GitHub에서 허용한 병합 방식·브랜치 보호·필수 검사·squash 기본 메시지는 실제 저장소 설정을 별도로 확인한다. 문서 작성이나 PR 생성으로 이를 바꾸거나 병합·배포를 실행하지 않는다.

MySQL 검증 워크플로는 base 전환과 Draft 해제 뒤에도 검사 기회를 갖도록 `edited`·`ready_for_review` 이벤트를 포함한다. 근거: [GitHub PR 워크플로 이벤트](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request).

## 부모 PR을 squash 병합한 뒤

부모의 기존 SHA는 squash 결과와 다르므로 base 이름만 바꾸면 부모 변경이 하위 diff에 다시 나타날 수 있다. 부모의 이전 끝 SHA와 하위 커밋 범위를 기록하고, 작업 파일·원격 동시 변경을 보호한 상태에서 다음을 수행한다.

1. 최신 `origin/develop`을 가져온다.
2. 하위 브랜치의 부모 이후 커밋만 새 `develop` 위로 옮긴다. 예를 들어 깨끗한 작업 디렉터리에서 `git rebase --onto origin/develop <부모의 이전 끝 SHA> <하위 브랜치>`를 사용한다.
3. 공유 이력 수정 범위를 확인하고 `--force-with-lease`로 갱신한다. 다른 작성자의 새 커밋을 덮어쓰지 않는다.
4. 하위 PR의 base를 `develop`로 바꾸고 부모 변경이 diff에서 빠졌는지 확인한다. 미해결 하위 PR이 있는 동안 부모 브랜치를 성급히 삭제하지 않는다.
5. 갱신한 SHA로 검사와 필요한 리뷰를 다시 받고 Draft를 해제한다. 기존 검증·승인을 새 SHA의 결과로 간주하지 않는다.

squash 병합한 작업 브랜치를 다음 작업에 그대로 재사용하지 않는다. 근거: [장기 브랜치의 squash 주의점](https://docs.github.com/en/pull-requests/reference/pull-request-merges#squashing-and-merging-a-long-running-branch).

## 이슈 연결과 취소

- 기본 브랜치 대상 PR이 이슈를 완료할 때 `Closes #번호`를 쓴다. 일부 작업이나 의존 PR은 우선 `Refs: #번호`로 연결하고, 최종 base 전환 후 종료 키워드를 확인한다. GitHub는 기본 브랜치가 아닌 PR의 종료 키워드를 해석하지 않는다. 근거: [GitHub 이슈 연결](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue).
- PR을 취소하는 것과 작업 파일을 폐기하는 것은 구분한다. 명시적 취소 요청이면 PR을 닫고 지정한 커밋 범위만 제거하며 작업 파일과 기존 스테이징을 보존한다. 파일 폐기는 별도 요청이 있을 때 수행한다.
- 원격 이력은 예상한 SHA를 조건으로 `--force-with-lease`를 사용한다. 다시 작성할 때는 목적별 새 브랜치·PR을 만들고 취소한 PR을 임의로 재개하지 않는다.

## 이번 작업의 적용 예

한국어 작성·리뷰·병합 규칙, 요청받은 기존 테스트 정리, MySQL 검증 기반은 세 PR로 나눈다. 앞의 두 PR은 `develop` 대상이며, MySQL PR은 전체 테스트가 통과하는 상태를 기준으로 검증하기 위해 테스트 정리 PR 위에 Draft로 올린다. MySQL PR에는 새 동작을 검증하는 테스트와 CI 설정을 함께 두고, 선행 병합 후 위 절차로 갱신한다.
