package eightjbbm.keepgo.auth.authorizationrequest;

public class AuthorizationRequestStorageException extends RuntimeException {
    public AuthorizationRequestStorageException() {
        super("OAuth authorization request storage failed");
    }
}
