# 현재 구현과 확인 필요 사항

확인일: 2026-11-01. [전체 목차](README.md)

이 문서는 시트의 원문을 고치지 않고 대조 결과를 기록한다. 로컬 소스의 정적 확인 결과이며 실제 AI 서버 호출이나 배포 검증 결과가 아니다. 여기서 열거하지 않은 필드까지 구현과 일치한다고 보장하지 않는다.

## 기능별 구현 대조

구현 차이는 각 [기능 문서](README.md)의 현재 구현과 확인 필요 사항에 기록한다.

전역 JSON 필드명 설정은 [application.yaml](../../src/main/resources/application.yaml)의 SNAKE_CASE다. multipart의 profileImage는 JSON 명명 규칙 대상이 아니다.

SSE는 연결 전 HTTP 오류와 연결 후 이벤트 오류를 구분한다. 원본의 “EventSource가 아닌 fetch?”는 확정된 클라이언트 구현 지시가 아니다. 원본에 없는 재전송 보장을 추가하지 않는다. [공통 SSE 오류](backend/common.md#sse-errors)를 참고한다.

AI의 [공통 오류](ai/common.md#errors)는 AI 탭에만 있다. 최신 탭에서 누락됐다는 이유만으로 폐기 또는 자동 상속을 확정하지 않는다.

## 원문 자체의 불명확성

- AI(v2)·AI(v3)의 추천 예시는 스마트 따옴표·누락 쉼표를 포함한다. 후보는 배열인데 설명에는 dict로 적힌 부분도 있다. 문서는 원문을 보존하며 자료형을 임의 수정하지 않는다.
- AI(v2)·AI(v3)의 영상 분석 후주에는 문장이 잘린 부분이 있다. 순서 보장·기간 미확정 처리의 잘린 문구를 추측해 완성하지 않는다.
- 원문의 요청·응답에 String, Integer, Float, Date, ...가 들어가거나 배열을 중괄호로 표시한 부분은 타입 설명이다. 실행 예제로 사용하려면 별도 정규화와 계약 검증이 필요하다.
- 빈 요청·응답·오류 셀은 미기재다. 인증 면제·오류 없음·본문 없음으로 해석하지 않는다.
- FE–BE v3 탭은 3개 API만 적힌 증분 명세다. 이 탭만으로 전체 서비스 API를 대체하지 않는다.

## 인증과 오류의 출처

기본 API 인증은 [ApiFilterChainConfig](../../src/main/java/eightjbbm/keepgo/auth/ApiFilterChainConfig.java)를 확인한다. 로그인·토큰 갱신은 각각 [LoginFilterChainConfig](../../src/main/java/eightjbbm/keepgo/auth/LoginFilterChainConfig.java), [RefreshFilterChainConfig](../../src/main/java/eightjbbm/keepgo/auth/refreshtokenhash/RefreshFilterChainConfig.java)의 별도 체인을 사용한다. 각 API의 빈 인증 셀을 인증 면제로 해석하지 않는다.

[예외 처리 규칙](../exception-handling.md)은 목표 구조다. code·trace_id·errors[]가 현재 모든 응답에 포함된다는 명세가 아니다. 현재 구현은 [GlobalExceptionHandler](../../src/main/java/eightjbbm/keepgo/util/exception/GlobalExceptionHandler.java) 및 개별 예외를 확인해야 한다. AI의 message/data 오류 형식과 BE의 ProblemDetail도 별개다.

## 갱신 절차

원본의 모든 탭을 다시 읽고 원본 행·요청·응답·후주를 함께 갱신한다. 시트 수정일과 실제 확인일을 구분한다. 새 사실은 해당 기능의 버전별 계약과 이 대조 문서에 기록하고, AI 운영 확인 기록은 증거 날짜와 출처를 유지한다. 문서 내용만으로 배포·실서버 검증 완료를 새로 선언하지 않는다.
