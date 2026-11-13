CREATE TABLE oauth_authorization_request (
    request_id CHAR(64) NOT NULL PRIMARY KEY,
    request_json TEXT NOT NULL,
    browser_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    INDEX idx_oauth_request_expiry (expires_at)
);
