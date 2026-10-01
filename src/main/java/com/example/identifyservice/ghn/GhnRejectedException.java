package com.example.identifyservice.ghn;

/**
 * GHN answered but refused this particular query (HTTP 400, for example an unsupported weight or route). It is a
 * clean answer, so callers fall back for this request without treating GHN as down.
 */
public class GhnRejectedException extends GhnUnavailableException {
    public GhnRejectedException(String message) {
        super(message);
    }
}
