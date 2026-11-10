package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.exception.BadGatewayException;

public class AiLlmInvalidResponseException extends BadGatewayException {
    public AiLlmInvalidResponseException() {
        super("AI_LLM_INVALID_RESPONSE");
    }
}
