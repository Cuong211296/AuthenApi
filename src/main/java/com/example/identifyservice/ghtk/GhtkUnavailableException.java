package com.example.identifyservice.ghtk;

/** GHTK could not give a usable answer (network, timeout, HTTP error, malformed body, not configured). */
public class GhtkUnavailableException extends RuntimeException {
    public GhtkUnavailableException(String message) {
        super(message);
    }

    public GhtkUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
