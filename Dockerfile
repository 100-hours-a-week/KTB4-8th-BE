# syntax=docker/dockerfile:1
# 백엔드 초기 이미지 파일 -> 휴이에게 전달 후 물어보기
# 빌드 단계에서

#이미지 지정 -> openJDK말고 일단 이클립스로 도커 공식홈페이지 권고 사항
# jammy는 우분투 기반이라는 뜻, AS build : 이 단계에서 build라는 이름을 붙인다.
FROM eclipse-temurin:25-jdk-jammy AS build

WORKDIR /workspace
# 명령어가 작업할 이미지 내부 디렉터리 지정
# 이거 내 로컬이 아니라 이미지 내부 경로임

COPY . .

RUN chmod +x gradlew \
    && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:25-jre-jammy AS runtime

WORKDIR /app

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home app
# 10001은 이 이미지에서 사용하는 UID/GID로 지정(임시)
# 로컬이 아닌 build단계에서부터 시작
# 복사한 파일 소유 사용자와 그룹을 일단 app으로 지정
# 위에서(build)에서 생성된 JAR파일을 선택
COPY --from=build --chown=app:app \
    /workspace/build/libs/*.jar /app/app.jar
# 최종 이미지에 app.jar라는 이름으로 저장할거임
# TODO(Build): bootJar 출력 이름을 app.jar로 고정한 뒤 와일드카드 대신
# /workspace/build/libs/app.jar를 정확히 복사한다.

USER app
# 일단 JAVA 애플리케이션은 root 권한으로 실행 안함


EXPOSE 8080
#일단 8080으로...SG도 8080으로 이건 그냥 8080포트를 컨테이너화 한다는 뜻

# TODO(Health Check): Actuator readiness Endpoint와 Runtime Image의
# Health Check 도구가 확정되면 HEALTHCHECK를 추가한다.

ENTRYPOINT [ "java", "-jar", "/app/app.jar" ]
# 실행할 기본 명령 지정
# 실제 실행 명령: java -jar /app/app.jar

# 백엔드는 멀티 스테이지 빌드로 JDK 기반 빌드 환경과 JRE 기반 실행 환경을 분리한다. 최종 이미지에는 실행에 필요한 JAR만
# 복사하여 이미지 크기와 불필요한 구성요소를 줄이고, 전용 일반 사용자로 실행하여 애플리케이션의 권한을 제한한다.
# 시크릿값은 항상 최소화!
