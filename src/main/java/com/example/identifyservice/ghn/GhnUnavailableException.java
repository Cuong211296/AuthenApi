package com.example.identifyservice.ghn;

/** GHN could not give a usable answer (network, timeout, HTTP error, malformed body, not configured). */
public class GhnUnavailableException extends RuntimeException {
    public GhnUnavailableException(String message) {
        super(message);
    }
}
