package eightjbbm.keepgo.chat.service;

import eightjbbm.keepgo.chat.entity.Chat;
import eightjbbm.keepgo.chat.repository.ChatRepository;
import eightjbbm.keepgo.chat.service.dto.ExtractSlotResponseV2;
import eightjbbm.keepgo.chat.service.dto.SendChatMessageCommandV2;
import eightjbbm.keepgo.chat.service.requeststore.ChatRequestStore;
import eightjbbm.keepgo.chat.service.requeststore.ChatRequestGuard;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.Coordinate;
import org.junit.jupiter.api.BeforeEach;
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
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(MockitoExtension.class)
class ChatServiceV2Test {

    private static final Long MEMBER_ID = 1L;
    private static final String CONTENT = "내일 강남에서 3시간 카페 가고 싶어";
    private static final String REPLY = "강남 카페를 찾아볼게요.";

    @Mock ChatRepository chatRepository;
    @Mock OutingCriteriaRepository outingCriteriaRepository;
    @Mock AiServerExtractSlotApiService aiServerService;
    @Mock MemberRepository memberRepository;
    @Mock ChatRequestStore requestStore;

    private final JsonMapper json = JsonMapper.builder().build();

    private MockMvc mvc;
    private Member member;
    private OutingCriteria criteria;
    private CountDownLatch released;

    @BeforeEach
    void setUp() {
        var service = new ChatServiceV2(
                chatRepository, outingCriteriaRepository, aiServerService,
                memberRepository, new ChatRequestGuard(requestStore));
        mvc = standaloneSetup(new TestController(service)).build();

        member = Member.create("테스터");
        ReflectionTestUtils.setField(member, "id", MEMBER_ID);
        criteria = OutingCriteria.emptyOutingCriteriaWithMemberId(MEMBER_ID);
        released = new CountDownLatch(1);

        when(requestStore.acquire(eq(MEMBER_ID), anyString())).thenReturn(true);
        doAnswer(invocation -> {
            released.countDown();
            return null;
        }).when(requestStore).release(eq(MEMBER_ID), anyString());
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(outingCriteriaRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(criteria));
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> {
            Chat chat = invocation.getArgument(0);
            ReflectionTestUtils.setField(chat, "id",
                    Boolean.TRUE.equals(chat.getIsByBot()) ? 101L : 100L);
            return chat;
        });
    }

    @Test
    void 정상_처리하면_accepted_다음_success를_보내고_채팅과_슬롯을_저장한다()
            throws Exception {
        var datetime = LocalDateTime.of(2026, 10, 21, 14, 0);
        var coordinate = new Coordinate(37.5f, 127.0f);

        givenSuccess(new ExtractSlotResponseV2.Slot(
                coordinate, "강남", datetime, "180", Set.of("카페")));

        var events = requestEvents();

        assertEquals(
                List.of("chat-reply.accepted", "chat-reply.success"),
                events.stream().map(Event::name).toList());
        assertEquals("100", events.get(0).id());
        assertEquals(events.get(0).id(), events.get(1).id());

        var accepted = events.get(0).data().path("chat");
        assertEquals(CONTENT, accepted.path("content").asText());
        assertFalse(accepted.path("is_by_bot").asBoolean());

        var success = events.get(1).data();
        assertEquals(REPLY, success.path("chat").path("content").asText());
        assertTrue(success.path("chat").path("is_by_bot").asBoolean());

        var slot = success.path("slot");
        assertEquals(37.5, slot.path("coordinate").path("lat").asDouble(), 0.001);
        assertEquals(127.0, slot.path("coordinate").path("lng").asDouble(), 0.001);
        assertEquals("강남", slot.path("location_name_requested").asText());
        assertEquals(
                "2026-10-21 14:00:00",
                slot.path("time_requested_datetime").asText());
        assertEquals(3, slot.path("time_available_hours").asInt());
        assertEquals("카페", slot.path("categories").get(0).asText());

        var chats = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository, times(2)).save(chats.capture());

        assertEquals(CONTENT, chats.getAllValues().get(0).getContent());
        assertFalse(chats.getAllValues().get(0).getIsByBot());
        assertEquals(REPLY, chats.getAllValues().get(1).getContent());
        assertTrue(chats.getAllValues().get(1).getIsByBot());
        assertSame(member, chats.getAllValues().get(1).getMember());

        var saved = ArgumentCaptor.forClass(OutingCriteria.class);
        verify(outingCriteriaRepository).save(saved.capture());

        assertEquals(MEMBER_ID, saved.getValue().getMemberId());
        assertEquals(180, saved.getValue().getUserAvailableTimeMinutes());
        assertEquals(
                datetime.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                saved.getValue().getRequestedDatetime());
        assertEquals("카페 선호", saved.getValue().getUserPreferenceSummary());
    }

    @Test
    void 첫_대화라면_빈_슬롯으로_AI를_호출하고_null_필드를_응답한다()
            throws Exception {
        when(outingCriteriaRepository.findById(MEMBER_ID))
                .thenReturn(Optional.empty());

        givenSuccess(new ExtractSlotResponseV2.Slot(
                null, null, null, null, null));

        var events = requestEvents();

        assertEquals("chat-reply.success", events.get(1).name());

        var previous = ArgumentCaptor.forClass(OutingCriteria.class);
        verify(aiServerService).extractSlot(eq(CONTENT), previous.capture());

        assertEquals(MEMBER_ID, previous.getValue().getMemberId());
        assertNull(previous.getValue().getRequestedDatetime());
        assertNull(previous.getValue().getUserAvailableTimeMinutes());
        assertTrue(previous.getValue().getCategories().isEmpty());

        var slot = events.get(1).data().path("slot");
        assertTrue(slot.path("time_requested_datetime").isNull());
        assertTrue(slot.path("time_available_hours").isNull());
    }

    @Test
    void 추출되지_않은_값은_유지하고_카테고리는_기존_값과_합친다()
            throws Exception {
        var datetime = LocalDateTime.of(2026, 10, 21, 14, 0);

        criteria = new OutingCriteria(
                MEMBER_ID,
                new Coordinate(37.5f, 127.0f),
                "강남",
                datetime.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                180,
                Set.of("카페"),
                "기존 취향");

        when(outingCriteriaRepository.findById(MEMBER_ID))
                .thenReturn(Optional.of(criteria));

        givenSuccess(new ExtractSlotResponseV2.Slot(
                null, null, null, null, Set.of("전시")));

        requestEvents();

        var saved = ArgumentCaptor.forClass(OutingCriteria.class);
        verify(outingCriteriaRepository).save(saved.capture());

        assertEquals(criteria.getUserCoordinate(), saved.getValue().getUserCoordinate());
        assertEquals("강남", saved.getValue().getRequestedLocationName());
        assertEquals(criteria.getRequestedDatetime(), saved.getValue().getRequestedDatetime());
        assertEquals(180, saved.getValue().getUserAvailableTimeMinutes());
        assertEquals(Set.of("카페", "전시"), saved.getValue().getCategories());
    }

    @Test
    void AI가_실패_응답을_반환하면_오류_이벤트로_종료한다() throws Exception {
        when(aiServerService.extractSlot(eq(CONTENT), any(OutingCriteria.class)))
                .thenReturn(new ExtractSlotResponseV2.Failure("failed", null));

        assertFailure(requestEvents());
    }

    @Test
    void AI_호출에서_예외가_발생하면_오류_이벤트로_종료한다() throws Exception {
        when(aiServerService.extractSlot(eq(CONTENT), any(OutingCriteria.class)))
                .thenThrow(new IllegalStateException("AI connection failed"));

        assertFailure(requestEvents());
    }

    @Test
    void AI_성공_응답의_필수_데이터가_없으면_오류_이벤트로_종료한다()
            throws Exception {
        when(aiServerService.extractSlot(eq(CONTENT), any(OutingCriteria.class)))
                .thenReturn(new ExtractSlotResponseV2.Success("success", null));

        assertFailure(requestEvents());
    }

    @Test
    void 가용_시간이_정수_시간으로_변환되지_않으면_저장하지_않는다()
            throws Exception {
        givenSuccess(new ExtractSlotResponseV2.Slot(
                null, null, null, "90", Set.of()));

        assertFailure(requestEvents());
    }

    private void givenSuccess(ExtractSlotResponseV2.Slot slot) {
        when(aiServerService.extractSlot(eq(CONTENT), any(OutingCriteria.class)))
                .thenReturn(new ExtractSlotResponseV2.Success(
                        "success",
                        new ExtractSlotResponseV2.SuccessData(
                                slot, "카페 선호", REPLY)));
    }

    private void assertFailure(List<Event> events) {
        assertEquals(
                List.of("chat-reply.accepted", "chat-reply.service-unavailable"),
                events.stream().map(Event::name).toList());
        assertEquals(events.get(0).id(), events.get(1).id());
        assertEquals(503, events.get(1).data().path("status").asInt());

        // 실패해도 사용자 메시지는 남기고, 봇 메시지와 슬롯은 저장하지 않습니다.
        verify(chatRepository, times(1)).save(any(Chat.class));
        verify(outingCriteriaRepository, never()).save(any(OutingCriteria.class));
    }

    private List<Event> requestEvents() throws Exception {
        var pending = mvc.perform(post("/test/chat"))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 가상 스레드가 SSE 처리를 끝낼 때까지 기다립니다.
        pending.getAsyncResult(5_000);
        assertTrue(released.await(5, TimeUnit.SECONDS), "작업 종료 후 실행 권한을 해제해야 합니다.");

        var requestId = ArgumentCaptor.forClass(String.class);
        verify(requestStore).acquire(eq(MEMBER_ID), requestId.capture());
        verify(requestStore).release(MEMBER_ID, requestId.getValue());

        var body = mvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_EVENT_STREAM))
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
            return service.sendChatMessage(
                    new SendChatMessageCommandV2(MEMBER_ID, CONTENT));
        }
    }
}
