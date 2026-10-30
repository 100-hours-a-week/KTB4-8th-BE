package eightjbbm.keepgo.util.client.ai.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/// 기동할 때 설정된 유형별 상한과 슬롯 행 수를 맞춘다.
@Component
@RequiredArgsConstructor
public class AiCallSlotInitializer implements ApplicationRunner {

    private final AiCallSlotRepository slots;
    private final AiCallProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        slots.ensureSlots(AiCallType.ANALYSIS, properties.analysisLimit());
        slots.ensureSlots(AiCallType.EMBEDDING, properties.embeddingLimit());
    }
}
