package eightjbbm.keepgo.util.dto;

import eightjbbm.keepgo.recommendation.outingcriteria.OutingCriteria;

import java.time.LocalDate;

public record ExtractSlotRequest(
        String chat,
        LocalDate date,
        OutingCriteria outingCriteria
) {}
