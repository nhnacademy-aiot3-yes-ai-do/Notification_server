package com.ecosphere.notification.security;

/** JWT에서 사용자 ID를 읽을 수 없을 때 사용하는 도메인 예외. */
public class JwtUserIdException extends RuntimeException {

    public JwtUserIdException(String message) {
        super(message);
    }

    public JwtUserIdException(String message, Throwable cause) {
        super(message, cause);
    }
}
