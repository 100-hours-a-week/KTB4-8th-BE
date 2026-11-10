package eightjbbm.keepgo.util.client.ai;

import eightjbbm.keepgo.util.exception.BadGatewayException;

public class AiLlmTimeoutException extends BadGatewayException {
    public AiLlmTimeoutException() {
        super("AI_LLM_TIMEOUT");
    }
}
