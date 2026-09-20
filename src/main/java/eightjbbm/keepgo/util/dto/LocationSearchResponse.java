package eightjbbm.keepgo.util.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public record LocationSearchResponse(
        Common common,
        Juso juso
) {
    public record Common(
            String totalCount,
            Integer currentPage,
            Integer countPerPage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Juso(
            String siNm,
            String sggNm,
            String emdNm
    ) {}
}
