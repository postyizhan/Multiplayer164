package com.github.postyizhan.multiplayer164.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * Minimal HTTP client built on JDK {@link HttpURLConnection}. Java 7 compatible,
 * no third-party dependencies. Used to talk to the local Terracotta process and
 * to fetch the public node list.
 */
public final class HttpUtil {
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private HttpUtil() {
    }

    /** Performs a GET request and returns the response body as a UTF-8 string. */
    public static String get(String url) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : readUtf8(in);
            if (code >= 400) {
                // Log the raw body (often an HTML error page) for diagnostics, but never
                // surface it to callers/UI — they get a clean status-only exception.
                System.err.println("[Multiplayer164] " + code + " from " + url
                        + " body=" + truncate(body, 512));
                throw new HttpStatusException(code, url);
            }
            return body;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Downloads {@code url} into {@code out}, returning the number of bytes written. */
    public static long download(String url, OutputStream out) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            if (code >= 400) {
                throw new HttpStatusException(code, url);
            }
            InputStream in = conn.getInputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                total += n;
            }
            return total;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Builds {@code base?k=v&k=v} from a list of [key, value] pairs, URL-encoding values. */
    public static String withQuery(String base, List<String[]> params) {
        StringBuilder sb = new StringBuilder(base);
        boolean first = base.indexOf('?') < 0;
        for (String[] pair : params) {
            sb.append(first ? '?' : '&');
            first = false;
            sb.append(encode(pair[0])).append('=').append(encode(pair[1]));
        }
        return sb.toString();
    }

    /** Builds a query string from a map (order not guaranteed). */
    public static String withQuery(String base, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(base);
        boolean first = base.indexOf('?') < 0;
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(first ? '?' : '&');
            first = false;
            sb.append(encode(e.getKey())).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ').replace('\r', ' ');
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (IOException e) {
            return s;
        }
    }

    private static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), "UTF-8");
    }
}
