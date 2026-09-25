package eightjbbm.keepgo.util;

public record MapCoordinatesToLocationNameResponse(
        MctlnResult result
) {
    record MctlnResult(
            MctlnItem item
    ) {
        record MctlnItem (
                MctlnStructure structure
        ) {
            record MctlnStructure (
                    String level2,
                    String level4A
            ) {}
        }
    }

    public String getAddress() {
        return result().item().structure().level2() + " " + result().item().structure().level4A();
    }
}
