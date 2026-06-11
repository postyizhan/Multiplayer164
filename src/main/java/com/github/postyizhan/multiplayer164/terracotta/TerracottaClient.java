package com.github.postyizhan.multiplayer164.terracotta;

import com.github.postyizhan.multiplayer164.util.HttpUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin HTTP client for the local Terracotta process, which exposes a REST API on
 * {@code http://127.0.0.1:<port>}. Endpoints mirror HMCL's usage:
 * <ul>
 *   <li>{@code GET /state} — poll current state</li>
 *   <li>{@code GET /state/ide} — return to idle</li>
 *   <li>{@code GET /state/scanning?player=&public_nodes=} — host: scan LAN worlds</li>
 *   <li>{@code GET /state/guesting?room=&player=&public_nodes=} — guest: join a room</li>
 *   <li>{@code GET /log?fetch=true} — export logs</li>
 * </ul>
 */
public final class TerracottaClient {
    private final int port;

    public TerracottaClient(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    /** Polls {@code /state} and parses the response. */
    public TerracottaState pollState() throws IOException {
        String body = HttpUtil.get(base("/state"));
        JsonObject obj = new JsonParser().parse(body).getAsJsonObject();
        return TerracottaState.fromJson(obj);
    }

    /**
     * Best-effort health check: returns {@code true} if {@code /state} responds. Used to
     * decide whether an already-running Terracotta can be reused instead of being killed
     * and relaunched (a relaunch re-triggers Windows UAC because Terracotta elevates for
     * EasyTier's TUN adapter).
     */
    public boolean ping() {
        try {
            pollState();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns Terracotta to the idle state. */
    public void setIdle() throws IOException {
        HttpUtil.get(base("/state/ide"));
    }

    /** Host: begin scanning for LAN worlds. Terracotta auto-detects the open LAN world. */
    public void startScanning(String playerName, List<String> nodes) throws IOException {
        List<String[]> query = new ArrayList<String[]>();
        query.add(new String[]{"player", playerName});
        for (String node : nodes) {
            query.add(new String[]{"public_nodes", node});
        }
        HttpUtil.get(HttpUtil.withQuery(base("/state/scanning"), query));
    }

    /** Guest: join a room by its invitation code. */
    public void startGuesting(String room, String playerName, List<String> nodes) throws IOException {
        List<String[]> query = new ArrayList<String[]>();
        query.add(new String[]{"room", room});
        query.add(new String[]{"player", playerName});
        for (String node : nodes) {
            query.add(new String[]{"public_nodes", node});
        }
        HttpUtil.get(HttpUtil.withQuery(base("/state/guesting"), query));
    }

    /** Asks the Terracotta core process to exit cleanly. */
    public void shutdown() throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base("/panic?peaceful=true")).openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            // The endpoint exits the Terracotta process from inside the handler, so the
            // connection may be closed before a full response is delivered. Trigger the
            // request and let callers ignore any IOException as a successful best-effort.
            conn.getResponseCode();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Fetches the Terracotta log text for troubleshooting. */
    public String fetchLog() throws IOException {
        return HttpUtil.get(base("/log?fetch=true"));
    }

    private String base(String path) {
        return "http://127.0.0.1:" + port + path;
    }
}
