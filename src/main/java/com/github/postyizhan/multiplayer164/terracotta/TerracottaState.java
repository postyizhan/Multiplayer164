package com.github.postyizhan.multiplayer164.terracotta;

import com.google.gson.JsonObject;

/**
 * Snapshot of the Terracotta process's current state, parsed from the JSON returned
 * by {@code GET /state}. The {@code state} discriminator field selects the kind; the
 * relevant payload fields differ per kind (host invite {@code room}, guest backup
 * {@code url}, exception {@code type}).
 *
 * <p>Mirrors the read-only HTTP contract of HMCL's {@code TerracottaState} but is an
 * independent, much smaller reimplementation (we keep only what the UI needs).
 */
public final class TerracottaState {
    public enum Kind {
        WAITING,
        HOST_SCANNING,
        HOST_STARTING,
        HOST_OK,
        GUEST_CONNECTING,
        GUEST_STARTING,
        GUEST_OK,
        EXCEPTION,
        UNKNOWN
    }

    public final Kind kind;
    public final int index;
    /** HOST_OK: the invitation/room code shown to the host. */
    public final String room;
    /** GUEST_OK: backup connection URL for the joined room. */
    public final String url;
    /** EXCEPTION: machine-readable error type, e.g. {@code guest-et-crash}. */
    public final String exceptionType;

    /**
     * Canonical exception type names, indexed exactly as Terracotta's own enum. The
     * {@code /state} JSON sends {@code type} as the integer ordinal into this list
     * (e.g. {@code 0} → {@code ping-host-fail}), so we translate the index back to the
     * stable kebab-case name the UI matches on.
     */
    private static final String[] EXCEPTION_TYPES = {
            "ping-host-fail",
            "ping-host-rst",
            "guest-et-crash",
            "host-et-crash",
            "ping-server-rst",
            "scaffolding-invalid-response"
    };

    private TerracottaState(Kind kind, int index, String room, String url, String exceptionType) {
        this.kind = kind;
        this.index = index;
        this.room = room;
        this.url = url;
        this.exceptionType = exceptionType;
    }

    public boolean isException() {
        return kind == Kind.EXCEPTION;
    }

    /** Parses a {@code /state} JSON object. Unrecognized states map to {@link Kind#UNKNOWN}. */
    public static TerracottaState fromJson(JsonObject obj) {
        String state = optString(obj, "state", "");
        int index = obj.has("index") && !obj.get("index").isJsonNull() ? obj.get("index").getAsInt() : -1;
        Kind kind = parseKind(state);
        String room = optString(obj, "room", null);
        String url = optString(obj, "url", null);
        String type = parseExceptionType(obj);
        return new TerracottaState(kind, index, room, url, type);
    }

    /**
     * Reads the exception {@code type}. Terracotta sends it as an integer ordinal into
     * {@link #EXCEPTION_TYPES}; older/looser builds might send the name directly, so we
     * accept both. Returns {@code null} when absent.
     */
    private static String parseExceptionType(JsonObject obj) {
        if (!obj.has("type") || obj.get("type").isJsonNull()) {
            return null;
        }
        try {
            int idx = obj.get("type").getAsInt();
            if (idx >= 0 && idx < EXCEPTION_TYPES.length) {
                return EXCEPTION_TYPES[idx];
            }
            return "unknown-" + idx;
        } catch (NumberFormatException notAnInt) {
            // Already a string name.
            return obj.get("type").getAsString();
        }
    }

    private static Kind parseKind(String state) {
        if ("waiting".equals(state)) {
            return Kind.WAITING;
        } else if ("host-scanning".equals(state)) {
            return Kind.HOST_SCANNING;
        } else if ("host-starting".equals(state)) {
            return Kind.HOST_STARTING;
        } else if ("host-ok".equals(state)) {
            return Kind.HOST_OK;
        } else if ("guest-connecting".equals(state)) {
            return Kind.GUEST_CONNECTING;
        } else if ("guest-starting".equals(state)) {
            return Kind.GUEST_STARTING;
        } else if ("guest-ok".equals(state)) {
            return Kind.GUEST_OK;
        } else if ("exception".equals(state)) {
            return Kind.EXCEPTION;
        }
        return Kind.UNKNOWN;
    }

    private static String optString(JsonObject obj, String key, String fallback) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return fallback;
    }

    @Override
    public String toString() {
        return "TerracottaState{" + kind + ", index=" + index
                + (room != null ? ", room=" + room : "")
                + (url != null ? ", url=" + url : "")
                + (exceptionType != null ? ", type=" + exceptionType : "") + '}';
    }
}
