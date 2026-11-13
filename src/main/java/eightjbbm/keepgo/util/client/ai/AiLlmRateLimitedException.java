package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.exception.BadGatewayException;

public class AiLlmRateLimitedException extends BadGatewayException {
    public AiLlmRateLimitedException() {
        super("AI_LLM_RATE_LIMITED");
    }
}
