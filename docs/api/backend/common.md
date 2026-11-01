# 헬스체크와 SSE 공통 규약 API 명세

확인일: 2026-11-01 (Asia/Seoul). [전체 목차](../README.md) · [공통 해석·인증·오류](../implementation-notes.md)

기능별 API 항목 아래에 버전별 계약을 모았다. 원본의 타입 표기·스마트 따옴표·불완전 예시는 text로 보존한다. 빈 셀은 미기재이며 없음·null과 다르다. 탭에 없는 버전은 폐기를 뜻하지 않는다. AI의 탭 버전과 실제 URL 버전은 별개다.

## API 항목

- [헬스체크](#health)
- [SSE 오류 본문](#sse-errors)

<a id="health"></a>

## 헬스체크

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v1-row-24"></a>

### v1 — FE - BE, 원본 24행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=1284625682#gid=1284625682&range=A24)

원본 메타데이터: B1: 최종 수정일 / D1: 260926 1349.

**번호**

```text
20
```

**도메인**

```text
기타
```

**API 엔드포인트 이름**

```text
헬스체크
```

**비고**

```text
검증 완료
```

**API 엔드포인트 주소**

```text
GET /actuator/health
```

**요청 본문**

미기재

**항목 설명 (G열)**

미기재

**응답 코드 (H열)**

미기재

**응답 본문**

미기재

**항목 설명 (J열)**

미기재

**발생할 수 있는 오류 코드 (K열)**

미기재

**외부 통신**

미기재

**캐시**

미기재

<a id="sse-errors"></a>

## SSE 오류 본문

다른 버전 탭에는 이 항목이 미기재되어 있다. 지원 중단으로 판단하지 않는다.

<a id="backend-v2-row-24"></a>

### v2 — FE - BE (v2), 원본 24행

[원본 시트](https://docs.google.com/spreadsheets/d/1ui37GlGUVHzM5lS3ZZE8TauRGyU035JWdjBCJ16TUOk/edit?gid=182265562#gid=182265562&range=A24)

원본 메타데이터: C1: 최종 수정일 / E1: 260926 1349.

**도메인**

```text
오류
```

**API 엔드포인트 이름**

```text
SSE 오류 본문
```

**응답 본문 (J열)**

```text
event: (기능 코드).(오류 코드)
id: 123
data: {
  (기존 오류 본문)
}
```

**항목 설명 (K열)**

```text
event 예시: chat-reply.service-unavailable
```
