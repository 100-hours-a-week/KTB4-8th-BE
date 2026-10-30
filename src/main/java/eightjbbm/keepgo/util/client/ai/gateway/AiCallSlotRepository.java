package eightjbbm.keepgo.util.client.ai.gateway;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/// DB 기반 전역 AI 호출 슬롯. 여러 인스턴스가 같은 테이블을 조건부 UPDATE로 경쟁한다.
///
/// 시각은 모두 DB 시간(UTC)을 쓴다. 임대가 만료된 슬롯은 holder가 남아 있어도 다시 획득할 수 있다.
@Repository
@RequiredArgsConstructor
public class AiCallSlotRepository {

    private final JdbcTemplate jdbcTemplate;

    /// 빈 슬롯 하나를 임대하고 holder를 돌려준다. 슬롯이 없으면 빈 값을 돌려준다.
    public Optional<String> acquire(AiCallType type, Duration lease) {
        String holder = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
                update ai_call_slot
                   set holder = ?, leased_until = timestampadd(second, ?, utc_timestamp(6))
                 where slot_type = ?
                   and (holder is null or leased_until is null or leased_until < utc_timestamp(6))
                 order by slot_no
                 limit 1
                """, holder, (int) lease.toSeconds(), type.name());
        return updated == 0 ? Optional.empty() : Optional.of(holder);
    }

    /// 이 호출이 잡고 있던 슬롯만 반납한다.
    public void release(AiCallType type, String holder) {
        jdbcTemplate.update(
                "update ai_call_slot set holder = null, leased_until = null where slot_type = ? and holder = ?",
                type.name(),
                holder);
    }

    /// 설정된 상한에 맞춰 슬롯 행 수를 맞춘다. 기동할 때 한 번 호출한다.
    public void ensureSlots(AiCallType type, int limit) {
        for (int slotNo = 1; slotNo <= limit; slotNo++) {
            jdbcTemplate.update(
                    "insert ignore into ai_call_slot (slot_type, slot_no) values (?, ?)", type.name(), slotNo);
        }
        jdbcTemplate.update("delete from ai_call_slot where slot_type = ? and slot_no > ?", type.name(), limit);
    }
}
