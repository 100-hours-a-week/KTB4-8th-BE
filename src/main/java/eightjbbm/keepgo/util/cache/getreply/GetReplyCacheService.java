package eightjbbm.keepgo.util.cache.getreply;

import eightjbbm.keepgo.util.dto.ExtractSlotRequest;
import eightjbbm.keepgo.util.dto.ExtractSlotResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GetReplyCacheService {
    private final GetReplyCacheRepository getReplyCacheRepository;
    private final GetReplyWorker worker;

    public void createRequest(GetReplyRequest request) {
        getReplyCacheRepository.create(request.memberId());
        worker.requestGetReply(
                request.memberId(),
                ExtractSlotRequest.from(request)
        );
    }

    /// 아직 꺼내지 않은 응답을 버린다. 대화 초기화 뒤 이전 대화의 답변이 새 대화에 나오지 않게 한다.
    public void discard(Long memberId) {
        getReplyCacheRepository.delete(memberId);
    }

    /// 응답 생성이 실패했으면 502를 던져, FE가 폴링 한도까지 기다리지 않고 바로 실패를 보여주게 한다.
    public Optional<ExtractSlotResponse.ExtractData> poll(Long memberId) {
        return getReplyCacheRepository.poll(memberId)
                .map(content -> {
                    if (content instanceof ExtractSlotResponse.ExtractData data) {
                        return data;
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "챗봇 응답 생성에 실패했습니다.");
                });
    }
}
