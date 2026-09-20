package eightjbbm.keepgo.util.dto;

import java.util.List;

public interface SearchAddressByKeywordResponse {
    record NoResult(
            List<AddressResult> searchResult,
            String message
    ) {}

    record Found(
            List<AddressResult> searchResult
    ) {}

    record AddressResult(
            String sdName,
            String sggName,
            String umdName
    ) {}
}
