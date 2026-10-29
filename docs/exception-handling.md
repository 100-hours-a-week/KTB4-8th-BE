# 예외 처리

이 문서는 예외 클래스 구조와 오류 응답 형식을 정한다. 설계 출처는 위키 [v2_exceptionhandling](https://github.com/100-hours-a-week/KTB4-8th-BE/wiki/v2_exceptionhandling)이며, 이 문서는 그중 확정된 목표 구조(TO-BE)를 규칙으로 옮긴 것이다. 새로 만드는 예외는 이 구조를 따른다. 현재 코드는 아직 이 구조가 아니며, 차이는 [알려진 예외](#알려진-예외)에 기록한다. 기존 구조는 언젠가 전환하되 지금 당장 일괄 변경하지 않는다.

## 오류 응답 형식

오류 응답은 RFC 9457 `ProblemDetail` 형식을 쓴다. 성공 응답을 감싸는 공통 래퍼는 두지 않는다.

```json
{
  "trace_id": "String",
  "code": "String",
  "type": "URI",
  "title": "String",
  "instance": "URI"
}
```

- `code`는 클라이언트가 오류를 구분하는 식별자다. `UPPER_SNAKE` 형식에 기능 접두사를 붙인다. 예: `WISHLIST_PLACE_NOT_FOUND`.
- `trace_id`는 명세에만 남겨 둔 필드이며 도입 여부는 미정이다. 도입이 결정되기 전까지 구현하지 않는다.

## 상태 코드

| 계층 | 상태 | 의미 | 세부 `code` |
| --- | --- | --- | --- |
| 시큐리티 | 401 | 인증 실패(AT·RT 검증 실패 등) | 나눈다 |
| 시큐리티 | 403 | 인가 실패 | fallback만 |
| 컨트롤러 | 400 | 요청을 해석할 수 없음(파싱·타입 불일치) | fallback만 |
| 컨트롤러 | 405 | 지원하지 않는 메서드 | fallback만 |
| 컨트롤러 | 413 | 본문·파일이 크기 제한을 넘음 | fallback만 |
| 컨트롤러 | 415 | 지원하지 않는 본문·파일 형식 | fallback만 |
| 컨트롤러 | 422 | 필드 값이 검증 규칙을 통과하지 못함 | 필드별 `errors[]` |
| 컨트롤러 | 429 | Rate Limit 초과 | fallback만 |
| 서비스 | 404 | 리소스가 없음 | 나눈다 |
| 서비스 | 409 | 요청이 서버 상태와 충돌함 | 나눈다 |
| 서비스 | 500 | 서버 내부 오류 | fallback만 |
| 서비스 | 502 | 외부 API 오류 | 나눈다 |
| 서비스 | 503 | 내부 API(AI 서버) 오류 | fallback만 |

"fallback만"인 상태는 클라이언트에 세부 원인을 나눠 줄 필요가 없는 경우다. 원인은 서버 로그로 남긴다.

## 예외 클래스 구조

예외는 3단계로 구성한다.

1. **base 클래스**: `ErrorResponseException`을 상속한다. `code`로 `messageSource`에서 `type`과 `title`을 조회해 `ProblemDetail`을 만든다. 메시지 코드는 `getTypeMessageCode()`, `getTitleMessageCode()`로 제공한다.
2. **상태 코드별 예외**: base 클래스를 상속한다(`NotFoundException`, `ConflictException` 등). 상태별 fallback `code`, `type`, `title`을 선언한다.
3. **기능별 예외**: 상태 코드별 예외를 상속하고 고유 `code`를 선언한다. 세부 `code`를 나누는 상태(401, 404, 409, 502)에만 만든다.

```java
public class PlaceNotFoundException extends NotFoundException {
    public PlaceNotFoundException() {
        super("WISHLIST_PLACE_NOT_FOUND");
    }
}
```

`messages.properties`에는 `code`별 `type`과 `title`만 선언한다.

```properties
problem.WISHLIST_PLACE_NOT_FOUND.type=/problems/wishlist-place-not-found
problem.WISHLIST_PLACE_NOT_FOUND.title=보관한 장소를 찾을 수 없습니다.
```

## 던지지 않는 예외

클라이언트 오류를 아래 예외로 표현하지 않는다. 전역 처리기에서 500으로 응답되거나 응답 형식이 달라진다.

- `IllegalArgumentException`, `RuntimeException`
- `ResponseStatusException`
- 인자 없는 `Optional.orElseThrow()`(`NoSuchElementException`)

```java
// 나쁜 예
var member = memberRepository.findById(id).orElseThrow();

// 좋은 예
var member = memberRepository.findById(id).orElseThrow(MemberNotFoundException::new);
```

`IllegalStateException`은 프로그래밍 오류(불변식 위반)에만 쓴다.

## 400과 422

- **400**: 본문을 파싱할 수 없거나 타입이 맞지 않는 경우. 응답을 세부적으로 나누지 않는다.
- **422**: Bean Validation을 통과하지 못한 경우. `errors[]`에 필드별 오류를 담는다.

```json
{
  "code": "String",
  "type": "URI",
  "title": "String",
  "instance": "URI",
  "errors": [
    { "code": "String", "detail": "String", "pointer": "JSON Pointer" }
  ]
}
```

검증 예외는 두 종류이며, `@RestControllerAdvice`에서 모두 422로 변환한다.

1. `MethodArgumentNotValidException`: `@Valid @RequestBody`만 있고 메서드 파라미터를 직접 검증하지 않은 경우. `getFieldErrors()`로 필드 오류를 얻는다.
2. `HandlerMethodValidationException`: `@NotNull`, `@Positive` 등 메서드 파라미터 제약으로 직접 검증한 경우. `getParameterValidationResults()`에서 본문이 아니면 `methodParameter.parameterName`, 본문(`ParameterErrors`)이면 `fieldErrors.field`로 필드 이름을 얻는다.

필드 이름은 오류 `code`로 매핑하고, `detail`은 `messages.properties`에서 조회한다. `pointer`는 필드 전체 경로(`a.b.c`)를 JSON Pointer(`/a/b/c`)로 바꾼다. 처리기는 응답을 반환해야 하며, 처리기 안에서 다시 예외를 던지지 않는다.

## 로깅

- 4xx는 `WARN` 이하로 남기고 스택 트레이스는 남기지 않는다.
- 5xx(500, 502, 503)는 `ERROR`로 남기고 원인 예외(cause)를 함께 남긴다.

## 알려진 예외

| 규칙 | 현재 코드 |
| --- | --- |
| 3단계 구조 | base 클래스와 `messageSource` 연결이 없다. `util/exception`의 상태 코드별 예외가 `ErrorResponseException`을 직접 상속하고 `ProblemDetail.forStatus(...)`를 쓴다. `code` 필드가 없다. |
| 기능별 예외 | `outingwishlist/common`에만 있다. |
| 던지지 않는 예외 | `IllegalArgumentException`, `ResponseStatusException`, `RuntimeException`이 여러 곳에서 쓰인다. `MemberService`에 인자 없는 `orElseThrow()`가 있다. |
| 422 처리 | `GlobalExceptionHandler`가 `MethodArgumentNotValidException` 처리기 안에서 `UnprocessableContentException`을 다시 던진다. `HandlerMethodValidationException` 처리가 없다. |
| 전역 처리기 | 비어 있는 `BasicExceptionHandler`가 `GlobalExceptionHandler`와 함께 있다. 정리는 별도 PR로 한다. |
