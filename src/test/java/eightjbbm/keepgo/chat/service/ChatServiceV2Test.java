package eightjbbm.keepgo.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.sun.net.httpserver.HttpServer;
import eightjbbm.keepgo.chat.entity.Chat;
import eightjbbm.keepgo.chat.repository.ChatRepository;
import eightjbbm.keepgo.chat.service.dto.SendChatMessageCommandV2;
import eightjbbm.keepgo.chat.service.requeststore.ChatRequestGuard;
import eightjbbm.keepgo.chat.service.requeststore.ChatRequestStore;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.client.ai.sse.AiSseClient;
import eightjbbm.keepgo.util.exception.GlobalExceptionHandler;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("채팅 전송 SSE (AI 서버 /v3/extract/stream 스텁)")
@ExtendWith(MockitoExtension.class)
class ChatServiceV2Test {

    private static final Long MEMBER_ID = 1L;
    private static final String CONTENT = "내일 강남에서 3시간 카페 가고 싶어";
    private static final String REPLY = "강남 카페를 찾아볼게요.";
    private static final String DELTAS = sse("delta", "{\"text\":\"강남 \"}") + sse("delta", "{\"text\":\"카페를 찾아볼게요.\"}");

    @Mock
    ChatRepository chatRepository;

    @Mock
    OutingCriteriaRepository outingCriteriaRepository;

    @Mock
    MemberRepository memberRepository;

    @Mock
    ChatRequestStore requestStore;

    private final JsonMapper json = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final AtomicReference<JsonNode> aiRequest = new AtomicReference<>();
    private final AtomicReference<String> aiPath = new AtomicReference<>();

    private HttpServer ai;
    private AiSseClient sseClient;
    private MockMvc mvc;
    private Member member;
    private OutingCriteria criteria;
    private CountDownLatch released;

    @BeforeEach
    void setUp() throws Exception {
        ai = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ai.start();
        sseClient = new AiSseClient(
                URI.create("http://127.0.0.1:" + ai.getAddress().getPort()),
                json,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                Duration.ofSeconds(10));

        var service = new ChatServiceV2(
                chatRepository,
                outingCriteriaRepository,
                new AiServerExtractSlotApiService(sseClient, json),
                memberRepository,
                new ChatRequestGuard(requestStore));
        mvc = standaloneSetup(new TestController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        member = Member.create("테스터");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        criteria = OutingCriteria.emptyOutingCriteriaWithMemberId(MEMBER_ID);
        released = new CountDownLatch(1);

        when(requestStore.acquire(eq(MEMBER_ID), anyString())).thenReturn(true);
        doAnswer(invocation -> {
                    released.countDown();
                    return null;
                })
                .when(requestStore)
                .release(eq(MEMBER_ID), anyString());
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(outingCriteriaRepository.findById(MEMBER_ID)).thenReturn(Optional.of(criteria));
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> {
            Chat chat = invocation.getArgument(0);
            ReflectionTestUtils.setField(chat, "id", Boolean.TRUE.equals(chat.getIsByBot()) ? 101L : 100L);
            return chat;
        });
    }

    @AfterEach
    void tearDown() {
        sseClient.close();
        ai.stop(0);
    }

    @Test
    @DisplayName("v3는 delta를 도착하는 대로 중계하고 done의 값을 저장한다")
    void v3RelaysDeltaAndSavesDone() throws Exception {
        var datetime = LocalDateTime.of(2026, 10, 21, 14, 0);

        stubAi(200, DELTAS + done("""
                                {"origin":{"lat":37.5,"lng":127.0},"region":"강남","datetime":"2026-10-21 14:00:00",
                                 "available_time":"180","category":["카페"]}"""));

        var events = requestEvents("/test/chat-v3");

        assertThat(events.stream().map(Event::name).toList())
                .isEqualTo(
                        List.of("chat-reply.accepted", "chat-reply.delta", "chat-reply.delta", "chat-reply.success"));
        assertThat(events.get(1).data().path("text").asText()).isEqualTo("강남 ");
        assertThat(events.get(2).data().path("text").asText()).isEqualTo("카페를 찾아볼게요.");
        assertThat(aiPath.get()).isEqualTo("/v3/extract/stream");

        var success = events.get(3).data();
        assertThat(success.path("chat").path("content").asText()).isEqualTo(REPLY);
        assertThat(success.path("chat").path("is_by_bot").asBoolean()).isTrue();

        var slot = success.path("slot");
        assertThat(slot.path("coordinate").path("lat").asDouble()).isCloseTo(37.5, within(0.001));
        assertThat(slot.path("location_name_requested").asText()).isEqualTo("강남");
        assertThat(slot.path("time_requested_datetime").asText()).isEqualTo("2026-10-21 14:00:00");
        assertThat(slot.path("time_available_hours").asInt()).isEqualTo(3);
        assertThat(slot.path("categories").get(0).asText()).isEqualTo("카페");

        var chats = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository, times(2)).save(chats.capture());
        assertThat(chats.getAllValues().get(0).getContent()).isEqualTo(CONTENT);
        assertThat(chats.getAllValues().get(1).getContent()).isEqualTo(REPLY);
        assertThat(chats.getAllValues().get(1).getIsByBot()).isTrue();
        assertThat(chats.getAllValues().get(1).getMember()).isSameAs(member);

        var saved = ArgumentCaptor.forClass(OutingCriteria.class);
        verify(outingCriteriaRepository).save(saved.capture());
        assertThat(saved.getValue().getUserAvailableTimeMinutes()).isEqualTo(180);
        assertThat(saved.getValue().getRequestedDatetime())
                .isEqualTo(datetime.atZone(ZoneId.of("Asia/Seoul")).toInstant());
        assertThat(saved.getValue().getUserPreferenceSummary()).isEqualTo("카페 선호");
    }

    @Test
    @DisplayName("v2는 delta를 중계하지 않고 이벤트 계약을 유지한다")
    void v2KeepsEventsWithoutDelta() throws Exception {
        stubAi(200, DELTAS + done("{\"region\":\"강남\"}"));

        var events = requestEvents("/test/chat");

        assertThat(events.stream().map(Event::name).toList())
                .isEqualTo(List.of("chat-reply.accepted", "chat-reply.success"));
        assertThat(events.get(0).id()).isEqualTo("100");
        assertThat(events.get(1).id()).isEqualTo(events.get(0).id());
        assertThat(events.get(0).data().path("chat").path("content").asText()).isEqualTo(CONTENT);
        assertThat(events.get(0).data().path("chat").path("is_by_bot").asBoolean())
                .isFalse();
        assertThat(events.get(1).data().path("chat").path("content").asText()).isEqualTo(REPLY);
        verify(outingCriteriaRepository).save(any(OutingCriteria.class));
    }

    @Test
    @DisplayName("첫 대화는 빈 나들이 조건으로 요청하고 null 필드를 응답한다")
    void firstChatSendsEmptyCriteria() throws Exception {
        when(outingCriteriaRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        stubAi(200, done("{}"));

        var events = requestEvents("/test/chat-v3");

        assertThat(events.get(1).name()).isEqualTo("chat-reply.success");

        var prevSlot = aiRequest.get().path("prev_slot");
        assertThat(aiRequest.get().path("chat").asText()).isEqualTo(CONTENT);
        assertThat(prevSlot.path("datetime").isNull()).isTrue();
        assertThat(prevSlot.path("available_time").isNull()).isTrue();
        assertThat(prevSlot.path("category").isEmpty()).isTrue();

        var slot = events.get(1).data().path("slot");
        assertThat(slot.path("time_requested_datetime").isNull()).isTrue();
        assertThat(slot.path("time_available_hours").isNull()).isTrue();
    }

    @Test
    @DisplayName("이전 나들이 조건의 일시를 공백 구분으로 보내고, 추출되지 않은 값은 유지하며 카테고리는 합친다")
    void sendsPrevSlotDatetimeAndMergesCriteria() throws Exception {
        var datetime = LocalDateTime.of(2026, 10, 21, 14, 0);

        criteria = new OutingCriteria(
                MEMBER_ID,
                new Coordinate(37.5f, 127.0f),
                "강남",
                datetime.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                180,
                Set.of("카페"),
                "기존 취향");

        when(outingCriteriaRepository.findById(MEMBER_ID)).thenReturn(Optional.of(criteria));

        stubAi(200, done("{\"category\":[\"전시\"]}"));

        requestEvents("/test/chat-v3");

        var prevSlot = aiRequest.get().path("prev_slot");
        assertThat(prevSlot.path("datetime").asText()).isEqualTo("2026-10-21 14:00:00");
        assertThat(prevSlot.path("available_time").asText()).isEqualTo("180");
        assertThat(prevSlot.path("region").asText()).isEqualTo("강남");
        assertThat(aiRequest.get().path("prev_query").asText()).isEqualTo("기존 취향");

        var saved = ArgumentCaptor.forClass(OutingCriteria.class);
        verify(outingCriteriaRepository).save(saved.capture());

        assertThat(saved.getValue().getUserCoordinate()).isEqualTo(criteria.getUserCoordinate());
        assertThat(saved.getValue().getRequestedLocationName()).isEqualTo("강남");
        assertThat(saved.getValue().getRequestedDatetime()).isEqualTo(criteria.getRequestedDatetime());
        assertThat(saved.getValue().getUserAvailableTimeMinutes()).isEqualTo(180);
        assertThat(saved.getValue().getCategories()).isEqualTo(Set.of("카페", "전시"));
    }

    @Test
    @DisplayName("v3에서 error 이벤트를 받으면 llm 오류는 bad-gateway 이벤트로 보내고 저장하지 않는다")
    void v3ErrorEventBecomesBadGateway() throws Exception {
        stubAi(200, DELTAS + sse("error", "{\"message\":\"llm_timeout\"}"));

        var events = requestEvents("/test/chat-v3");

        assertThat(events.stream().map(Event::name).toList())
                .isEqualTo(List.of(
                        "chat-reply.accepted", "chat-reply.delta", "chat-reply.delta", "chat-reply.bad-gateway"));
        assertThat(events.get(3).data().path("status").asInt()).isEqualTo(502);
        assertNothingSaved();
    }

    @Test
    @DisplayName("v3에서 llm이 아닌 error 이벤트는 service-unavailable 이벤트로 보내고 저장하지 않는다")
    void v3OtherErrorEventBecomesServiceUnavailable() throws Exception {
        stubAi(200, sse("error", "{\"message\":\"internal_server_error\"}"));

        var events = requestEvents("/test/chat-v3");

        assertThat(events.stream().map(Event::name).toList())
                .isEqualTo(List.of("chat-reply.accepted", "chat-reply.service-unavailable"));
        assertThat(events.get(1).data().path("status").asInt()).isEqualTo(503);
        assertNothingSaved();
    }

    @Test
    @DisplayName("v2는 error 이벤트도 service-unavailable로 유지한다")
    void v2ErrorEventStaysServiceUnavailable() throws Exception {
        stubAi(200, DELTAS + sse("error", "{\"message\":\"llm_timeout\"}"));

        var events = requestEvents("/test/chat");

        assertThat(events.stream().map(Event::name).toList())
                .isEqualTo(List.of("chat-reply.accepted", "chat-reply.service-unavailable"));
        assertThat(events.get(1).data().path("status").asInt()).isEqualTo(503);
        assertNothingSaved();
    }

    @Test
    @DisplayName("done 없이 스트림이 끊기면 부분 응답을 저장하지 않는다")
    void streamEndingWithoutDoneSavesNothing() throws Exception {
        stubAi(200, DELTAS);

        var v3 = requestEvents("/test/chat-v3");

        assertThat(v3.stream().map(Event::name).toList())
                .isEqualTo(List.of(
                        "chat-reply.accepted",
                        "chat-reply.delta",
                        "chat-reply.delta",
                        "chat-reply.service-unavailable"));
        assertNothingSaved();
    }

    @Test
    @DisplayName("v3는 스트림 시작 전 AI HTTP 오류를 SSE 없이 HTTP 오류로 응답한다")
    void v3BeforeStreamErrorIsHttpError() throws Exception {
        stubAi(504, "{\"message\":\"llm_timeout\"}", "application/json");

        mvc.perform(post("/test/chat-v3")).andExpect(status().isBadGateway());

        assertThat(released.await(5, TimeUnit.SECONDS))
                .as("실패해도 실행 권한을 해제해야 합니다.")
                .isTrue();
        assertNothingSaved();
    }

    @Test
    @DisplayName("v2는 스트림 시작 전 AI HTTP 오류도 service-unavailable 이벤트로 보낸다")
    void v2BeforeStreamErrorStaysEvent() throws Exception {
        stubAi(504, "{\"message\":\"llm_timeout\"}", "application/json");

        var events = requestEvents("/test/chat");

        assertThat(events.stream().map(Event::name).toList())
                .isEqualTo(List.of("chat-reply.accepted", "chat-reply.service-unavailable"));
        assertNothingSaved();
    }

    @Test
    @DisplayName("done에 나들이 조건이나 봇 메시지가 없으면 저장하지 않는다")
    void doneWithoutRequiredDataSavesNothing() throws Exception {
        stubAi(200, sse("done", "{\"slot\":{},\"query\":\"q\"}"));

        var events = requestEvents("/test/chat-v3");

        assertThat(events.get(1).name()).isEqualTo("chat-reply.service-unavailable");
        assertNothingSaved();
    }

    @Test
    @DisplayName("가용 시간이 정수 시간으로 변환되지 않으면 저장하지 않는다")
    void invalidAvailableTimeSavesNothing() throws Exception {
        stubAi(200, done("{\"available_time\":\"90\"}"));

        var events = requestEvents("/test/chat-v3");

        assertThat(events.get(1).name()).isEqualTo("chat-reply.service-unavailable");
        assertNothingSaved();
    }

    private void assertNothingSaved() {
        // 실패해도 사용자 메시지는 남기고, 봇 메시지와 나들이 조건은 저장하지 않습니다.
        verify(chatRepository, times(1)).save(any(Chat.class));
        verify(outingCriteriaRepository, never()).save(any(OutingCriteria.class));
    }

    private static String sse(String event, String data) {
        return "event: " + event + "\ndata: " + data.replace("\n", "") + "\n\n";
    }

    private static String done(String slot) {
        return sse("done", "{\"slot\":" + slot + ",\"query\":\"카페 선호\",\"bot_message\":\"" + REPLY + "\"}");
    }

    private void stubAi(int status, String body) {
        stubAi(status, body, "text/event-stream");
    }

    private void stubAi(int status, String body, String contentType) {
        ai.createContext("/", exchange -> {
            aiPath.set(exchange.getRequestURI().getPath());
            aiRequest.set(json.readTree(exchange.getRequestBody().readAllBytes()));
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private List<Event> requestEvents(String path) throws Exception {
        var pending =
                mvc.perform(post(path)).andExpect(request().asyncStarted()).andReturn();

        // 가상 스레드가 SSE 처리를 끝낼 때까지 기다립니다.
        pending.getAsyncResult(5_000);
        assertThat(released.await(5, TimeUnit.SECONDS))
                .as("작업 종료 후 실행 권한을 해제해야 합니다.")
                .isTrue();

        var requestId = ArgumentCaptor.forClass(String.class);
        verify(requestStore).acquire(eq(MEMBER_ID), requestId.capture());
        verify(requestStore).release(MEMBER_ID, requestId.getValue());

        var body = mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        return java.util.Arrays.stream(body.replace("\r\n", "\n").split("\n\n"))
                .filter(event -> !event.isBlank())
                .map(this::parseEvent)
                .toList();
    }

    private Event parseEvent(String text) {
        String name = null;
        String id = null;
        var data = new StringBuilder();

        for (var line : text.lines().toList()) {
            if (line.startsWith("event:")) {
                name = line.substring(6).stripLeading();
            } else if (line.startsWith("id:")) {
                id = line.substring(3).stripLeading();
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring(5).stripLeading());
            }
        }

        return new Event(name, id, json.readTree(data.toString()));
    }

    private record Event(String name, String id, JsonNode data) {}

    @RestController
    static class TestController {
        private final ChatServiceV2 service;

        TestController(ChatServiceV2 service) {
            this.service = service;
        }

        @PostMapping(value = "/test/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter send() {
            return service.sendChatMessage(new SendChatMessageCommandV2(MEMBER_ID, CONTENT));
        }

        @PostMapping(value = "/test/chat-v3", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter stream() {
            return service.streamChatMessage(new SendChatMessageCommandV2(MEMBER_ID, CONTENT));
        }
    }
}
