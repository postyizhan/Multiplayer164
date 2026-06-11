package com.github.postyizhan.multiplayer164.util;

import java.io.IOException;

/**
 * Raised when an HTTP request completes with a {@code >= 400} status code. Carries the
 * numeric status separately from the (often HTML) response body so callers can build a
 * clean, user-facing message and keep the raw body out of the UI — it is logged instead.
 */
public final class HttpStatusException extends IOException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final String url;

    public HttpStatusException(int statusCode, String url) {
        super("HTTP " + statusCode + " for " + url);
        this.statusCode = statusCode;
        this.url = url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getUrl() {
        return url;
    }
}
