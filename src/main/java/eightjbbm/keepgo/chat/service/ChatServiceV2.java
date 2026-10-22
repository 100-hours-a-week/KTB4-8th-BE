package eightjbbm.keepgo.chat.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import eightjbbm.keepgo.chat.entity.Chat;
import eightjbbm.keepgo.chat.repository.ChatRepository;
import eightjbbm.keepgo.chat.service.dto.ExtractSlotResponseV2;
import eightjbbm.keepgo.chat.service.dto.SendChatMessageCommandV2;
import eightjbbm.keepgo.chat.service.requeststore.ChatRequestGuard;
import eightjbbm.keepgo.member.entity.Member;
import eightjbbm.keepgo.member.repository.MemberRepository;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;
import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteriaRepository;
import eightjbbm.keepgo.util.Coordinate;
import eightjbbm.keepgo.util.exception.BadRequestException;
import eightjbbm.keepgo.util.exception.NotFoundException;
import eightjbbm.keepgo.util.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceV2 {

    private final ChatRepository chatRepository;
    private final OutingCriteriaRepository outingCriteriaRepository;
    private final AiServerExtractSlotApiService aiServerService;
    private final MemberRepository memberRepository;
    private final ChatRequestGuard requestGuard;

    public SseEmitter sendChatMessage(SendChatMessageCommandV2 command) {
        if (command.memberId() == null
                || command.content() == null
                || command.content().isBlank()) {
            throw new BadRequestException();
        }

        var lease = requestGuard.acquire(command.memberId());

        try {
            var member = memberRepository.findById(command.memberId())
                    .orElseThrow(NotFoundException::new);

            var criteria = outingCriteriaRepository.findById(command.memberId())
                    .orElseGet(() ->
                            OutingCriteria.emptyOutingCriteriaWithMemberId(command.memberId()));

            var chat = chatRepository.save(Chat.from(member, command.content()));
            var connection = new ChatConnection(chat.getId().toString());

            Thread.startVirtualThread(() ->
                    reply(command, member, criteria, chat, connection, lease));

            return connection.emitter;
        } catch (RuntimeException | Error error) {
            try {
                lease.close();
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
            throw error;
        }
    }

    private void reply(
            SendChatMessageCommandV2 command,
            Member member,
            OutingCriteria criteria,
            Chat chat,
            ChatConnection connection,
            ChatRequestGuard.Lease lease
    ) {
        String event = "service-unavailable";
        Object payload = unavailable();

        try {
            connection.send("accepted", Map.of(
                    "chat", new ChatPayload(chat.getContent(), false)));

            var response = aiServerService.extractSlot(command.content(), criteria);

            if (!(response instanceof ExtractSlotResponseV2.Success success)
                    || success.data() == null
                    || success.data().slot() == null
                    || success.data().botMessage() == null) {
                throw new ServiceUnavailableException();
            }

            var data = success.data();
            var updated = updateCriteria(criteria, data);

            var successPayload = Map.of(
                    "chat", new ChatPayload(data.botMessage(), true),
                    "slot", SlotPayload.from(updated));

            chatRepository.save(new Chat(member, data.botMessage(), true));
            outingCriteriaRepository.save(updated);

            payload = successPayload;
            event = "success";
        } catch (RuntimeException error) {
            log.warn("채팅 처리 실패: memberId={}", command.memberId(), error);
        } finally {
            try {
                lease.close();
            } catch (RuntimeException error) {
                log.error("채팅 실행 권한 해제 실패: memberId={}",
                        command.memberId(), error);

                event = "service-unavailable";
                payload = unavailable();
            }

            connection.finish(event, payload);
        }
    }

    private ProblemDetail unavailable() {
        return ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private OutingCriteria updateCriteria(
            OutingCriteria previous,
            ExtractSlotResponseV2.SuccessData data
    ) {
        var slot = data.slot();

        // AI availableTime은 분 단위 숫자 문자열이라는 전제입니다.
        var minutes = slot.availableTime() == null
                ? null
                : Integer.valueOf(slot.availableTime().trim());

        var updated = previous.update(
                slot.origin(),
                slot.region(),
                slot.datetime() == null ? null : slot.datetime()
                        .atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                minutes,
                slot.category(),
                data.query());

        var availableMinutes = updated.getUserAvailableTimeMinutes();
        if (availableMinutes != null
                && (availableMinutes < 0 || availableMinutes % 60 != 0)) {
            throw new ServiceUnavailableException();
        }

        return updated;
    }

    final class ChatConnection {

        final SseEmitter emitter = new SseEmitter(180_000L);

        private final String eventId;
        private boolean closed;

        ChatConnection(String eventId) {
            this.eventId = eventId;

            emitter.onCompletion(this::close);
            emitter.onError(error -> close());
            emitter.onTimeout(this::fail);
        }

        synchronized void send(String event, Object data) {
            if (closed) {
                return;
            }

            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name("chat-reply." + event)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (IOException disconnected) {
                closed = true;
            } catch (RuntimeException error) {
                closed = true;
                emitter.completeWithError(error);
            }
        }

        synchronized void finish(String event, Object data) {
            if (closed) {
                return;
            }

            send(event, data);

            if (!closed) {
                closed = true;
                emitter.complete();
            }
        }

        void fail() {
            finish("service-unavailable",
                    ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        private synchronized void close() {
            closed = true;
        }
    }

    record ChatPayload(
            String content,
            @JsonProperty("is_by_bot") boolean isByBot
    ) {}

    record SlotPayload(
            Coordinate coordinate,
            @JsonProperty("location_name_requested") String locationName,
            @JsonProperty("time_requested_datetime") String datetime,
            @JsonProperty("time_available_hours") Integer availableHours,
            Set<String> categories
    ) {
        static SlotPayload from(OutingCriteria criteria) {
            var datetime = criteria.getRequestedDatetime();
            var minutes = criteria.getUserAvailableTimeMinutes();

            return new SlotPayload(
                    criteria.getUserCoordinate(),
                    criteria.getRequestedLocationName(),
                    datetime == null ? null : datetime
                            .atZone(ZoneId.of("Asia/Seoul"))
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    minutes == null ? null : minutes / 60,
                    criteria.getCategories());
        }
    }
}