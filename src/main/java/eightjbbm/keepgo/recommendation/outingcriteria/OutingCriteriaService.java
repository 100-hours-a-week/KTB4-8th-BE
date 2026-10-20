package eightjbbm.keepgo.recommendation.outingcriteria;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutingCriteriaService {
    private final OutingCriteriaRepository outingCriteriaRepository;
}
