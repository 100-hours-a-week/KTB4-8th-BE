# 코드 규칙

이 문서는 사람과 AI 에이전트가 같은 기준으로 코드를 작성하고 리뷰하기 위한 규칙이다. 규칙은 현재 코드에서 다수가 따르는 패턴을 기준으로 정했고, 앞으로 작성·수정하는 코드에 적용한다. 규칙과 다른 기존 코드는 [알려진 예외](#알려진-예외)에 기록하며, 해당 파일을 수정할 때 함께 맞춘다. 기존 코드를 규칙에 맞추려고 일괄 리팩터링하지 않는다.

포매팅은 Spotless가 Palantir Java Format으로 강제한다. 설정은 `build.gradle`의 `spotless` 블록에 있고, `./gradlew spotlessApply`로 고친다. 정적 분석 도구는 아직 도입하지 않았고, 도입은 [PR 전략](pr-strategy.md)에 따라 별도 PR로 진행한다. 예외와 오류 응답은 [예외 처리](exception-handling.md)를 따른다.

## 패키지 구조

최상위 패키지는 기능 단위로 나누고(`auth`, `chat`, `member`, `recommendation` 등), 기능 안은 계층형으로 구성한다.

```
member/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
└── MemberMapper.java
```

- 기능 사이에서 공유하는 값 타입이나 설정은 그 기능을 쓰는 쪽이 아니라 공용 패키지에 둔다. `sandbox`에 있는 타입을 운영 코드에서 참조하지 않는다.
- 헥사고날 구조(`adapter/in|out`, `application/domain`)는 `outingwishlist`에서만 허용한다. 다른 기능에 도입하려면 먼저 [ADR](adr/)로 근거를 남긴다.

## 이름

- 클래스 이름에 `V2`, `V3` 같은 버전 접미사를 붙이지 않는다. API 버전은 URL(`/api/v2/...`)로 구분한다. 이전 버전과 새 버전이 함께 있는 동안에만 기능 패키지 아래에 `v2` 하위 패키지를 두고, 이전 버전은 대체가 끝나면 삭제한다.
- 계층별 역할을 이름에 드러낸다: `*Controller`, `*Service`, `*Repository`(Spring Data 또는 저장소 추상화), `*Client`(외부 API), `*Properties`(설정).

```java
// 나쁜 예
public class ChatServiceV2 { }

// 좋은 예: chat/service/ChatService.java, URL은 /api/v2/chats
public class ChatService { }
```

## DTO와 변환

DTO는 `record`로 작성하고, 계층에 따라 접미사를 붙인다.

| 접미사 | 위치 | 용도 |
| --- | --- | --- |
| `Request` | Controller 입력 | HTTP 요청 본문·파라미터 |
| `Command` | Service 입력 | 유스케이스 입력 |
| `Result` | Service 출력 | 유스케이스 결과 |
| `Response` | Controller 출력 | HTTP 응답 본문 |

이름은 `<동사><대상><접미사>` 형식으로 짓는다. 예: `UpdateMemberInfoRequest`, `UpdateMemberInfoCommand`.

변환은 대상 DTO의 `static from(...)`을 기본으로 쓴다. 다음 경우에만 MapStruct를 쓴다.

- 필드가 많아 손으로 옮기는 코드가 반복될 때
- 중첩 객체·컬렉션을 변환할 때
- 이름이 다른 필드를 매핑해야 할 때

MapStruct 매퍼는 `componentModel = "spring"`으로 선언하고 생성자로 주입한다. `INSTANCE` 정적 싱글턴은 쓰지 않는다. 이름은 `<기능>Mapper`로 하고 기능 패키지 루트에 둔다.

```java
// 나쁜 예
var command = MemberMapper.INSTANCE.toCommand(request);

// 좋은 예
@RequiredArgsConstructor
public class MemberController {
    private final MemberMapper memberMapper;
    // ...
    var command = memberMapper.toCommand(request);
}
```

## 계층 책임

- Controller는 Service만 호출하고 Repository를 직접 사용하지 않는다. Controller는 요청을 `Command`로 바꾸고, Service를 호출하고, `Result`를 `Response`로 바꾼다.
- 요청 검증은 `@Valid`와 Bean Validation 제약 어노테이션으로 한다. 검증 실패 응답은 [예외 처리](exception-handling.md#400과-422)를 따른다.

## 트랜잭션

- `@Transactional`은 Service의 public 메서드에만 붙인다. 클래스 단위로 붙이지 않는다.
- 조회 메서드에는 `@Transactional(readOnly = true)`를 명시한다.
- Mapper, Controller, Repository 구현체에는 붙이지 않는다.

```java
@Transactional(readOnly = true)
public GetMemberInfoResult getMemberInfo(long memberId) { ... }

@Transactional
public UpdateMemberInfoResult updateMemberInfo(UpdateMemberInfoCommand command) { ... }
```

## JPA 엔티티

- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 막는다.
- `@Setter`를 쓰지 않는다. 상태 변경은 의도를 드러내는 도메인 메서드(`updateNickname`, `delete`)로 한다.
- 연관 관계는 `fetch = FetchType.LAZY`를 명시한다. `@OneToOne`, `@ManyToOne`은 기본값이 EAGER이므로 반드시 명시한다.
- enum은 `@Enumerated(EnumType.STRING)`으로 저장한다.
- 시간 필드는 `Instant` 타입에 `createdAt`, `updatedAt`, `deletedAt` 이름을 쓴다. 기존 필드 이름을 바꿀 때는 [DB 마이그레이션](database-migrations.md) 절차를 따르는 별도 작업으로 한다.

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
    @Id @GeneratedValue
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private File profileImage;

    private Instant createdAt;

    public void updateNickname(String nickname) { ... }
}
```

## 테스트

- 모든 테스트 클래스와 메서드에 한국어 `@DisplayName`을 붙인다. `@Nested` 클래스에는 대상이나 상황을 적는다.
- 메서드 이름은 짧은 영어 camelCase로 짓는다. `test1` 같은 무의미한 이름을 쓰지 않는다.
- 테스트 클래스 이름은 `<대상>Test`로 한다. `*Tests` 복수형을 쓰지 않는다.
- 검증은 AssertJ `assertThat`으로 한다.
- 단위 테스트는 `src/test`, 실제 MySQL이 필요한 통합 테스트는 `src/mysqlIntegrationTest`에 둔다.

```java
@DisplayName("코스 위시리스트 조회")
class OutingWishlistServiceTest {

    @Test
    @DisplayName("존재하지 않는 코스면 404 예외를 던진다")
    void throwsNotFoundWhenCourseMissing() {
        assertThatThrownBy(() -> service.getCourse(missingId))
                .isInstanceOf(OutingCourseNotFoundException.class);
    }
}
```

## 기타

- **의존성 주입**: `@RequiredArgsConstructor`로 생성자 주입한다. 필드 주입(`@Autowired`)을 쓰지 않는다.
- **설정값**: `keepgo.*` 접두사를 쓰는 record `@ConfigurationProperties`로 읽는다. `@Value`를 쓰지 않는다.
- **로깅**: `@Slf4j`를 쓴다. 객체를 통째로 `toString()`해서 남기지 않고, 필요한 식별자만 남긴다. 예외 로그 수준은 [예외 처리](exception-handling.md#로깅)를 따른다.
- **주석**: 한국어로 쓰고, 문서 주석은 `///` 형식을 쓴다. 주석 처리된 코드는 커밋하지 않는다.
- **`var`**: 우변에서 타입이 드러날 때만 쓴다(`new`, `from(...)`, 이름에 타입이 있는 팩토리).

## 리뷰 체크리스트

아래 항목 중 구조로 판정할 수 있는 것(`Controller`→`Repository` 의존, `@Value`, 필드 주입, `@Transactional` 위치, 엔티티 setter)은 `ArchitectureRulesTest`가 검사한다. 리뷰에서는 나머지를 본다.

- [ ] 기능 패키지 안이 계층형으로 구성되어 있는가. 헥사고날 구조는 `outingwishlist`에만 있는가.
- [ ] 클래스 이름에 버전 접미사가 없는가.
- [ ] DTO가 `record`이고 `Request`/`Command`/`Result`/`Response` 접미사를 따르는가.
- [ ] 변환은 `from(...)`을 쓰는가. MapStruct는 복잡한 변환에만 쓰고 Bean으로 주입하는가.
- [ ] Controller가 Repository를 직접 쓰지 않는가. 요청 검증에 `@Valid`를 쓰는가.
- [ ] `@Transactional`이 Service 메서드에만 있고, 조회 메서드에 `readOnly = true`가 있는가.
- [ ] 엔티티가 `PROTECTED` 기본 생성자, `@Setter` 없음, `LAZY`, `EnumType.STRING` 규칙을 따르는가.
- [ ] 테스트에 한국어 `@DisplayName`이 있고 AssertJ를 쓰는가.
- [ ] `@Value`, 필드 주입, 객체 `toString()` 로깅, 주석 처리된 코드가 없는가.
- [ ] 예외가 [예외 처리](exception-handling.md) 규칙을 따르는가.

## 알려진 예외

아래는 규칙이 정해지기 전에 작성된 코드다. 해당 파일을 수정할 때 함께 규칙에 맞춘다.

| 규칙 | 현재 코드 |
| --- | --- |
| 헥사고날 구조 | `outingwishlist`는 의도된 예외다. 다른 기능으로 확산하지 않는다. |
| 패키지 구조 | `interestextraction`은 엔티티가 `repository` 아래에, Command/Result가 `service` 루트에 있다. `chat`은 `dto`, `controller/dto`, `service/dto`가 함께 있다. `recommendation`은 일부 Response가 기능 루트에 있다. |
| 공용 코드 위치 | 공용 `Address`가 `sandbox.ds`에, `QuerydslConfig`가 `outingwishlist` 안에 있다. |
| 버전 접미사 | `ChatControllerV2`, `ChatServiceV2`, `RecommendationControllerV2/V3`, `InterestExtractionControllerV3`, `InterestExtractionServiceV2/V3`, `YoutubeApiServiceV2`와 관련 DTO |
| MapStruct | `MemberMapper`, `ChatMapper` 등이 `INSTANCE` 싱글턴으로 사용된다. `InterestExtractionMapper`는 싱글턴 이름도 다르다. |
| 트랜잭션 | `outingwishlist` 외의 조회 메서드에 `readOnly`가 없다. |
| 엔티티 생성자 | `Member`, `OAuthAccount`, `Chat`, `VideoAnalysis`, `MemberLikedVideo`, `File`, `OutingGuide`, `OutingPlace`, `OutingEvent`가 public 기본 생성자를 쓴다. |
| fetch 전략 | `Member.profileImage`가 기본값 EAGER인 `@OneToOne`이다. |
| 시간 필드 | `OutingCourse`, `VideoAnalysis`, `MemberLikedVideo`, `OutingWishlistEntry`가 `createDatetime`/`deleteDatetime`을 쓴다. |
| 테스트 | 한국어 snake_case 메서드 이름, `@DisplayName` 누락, JUnit `assertEquals` 사용이 섞여 있다. `GoogleApiClientUnitTests.test1` |
| 설정값 | `OutingCriteriaProperties`에 `keepgo.*` 접두사가 없다. |
| 로깅·주석 | `MemberService`에 `toString()` 로깅과 주석 처리된 코드가 있다. |
