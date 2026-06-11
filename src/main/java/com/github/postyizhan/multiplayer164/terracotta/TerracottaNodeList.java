package com.github.postyizhan.multiplayer164.terracotta;

import com.github.postyizhan.multiplayer164.util.HttpUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetches the Terracotta public coordination node list from {@link TerracottaMetadata#NODE_LIST_URL}.
 * The result is cached after the first successful fetch. These node URLs are passed to
 * the Terracotta process as {@code public_nodes} query parameters when hosting/joining.
 */
public final class TerracottaNodeList {
    private static volatile List<String> cached;

    private TerracottaNodeList() {
    }

    /** Returns the node URLs, fetching once and caching. Never null; empty on failure. */
    public static List<String> fetch() {
        List<String> local = cached;
        if (local != null) {
            return local;
        }
        synchronized (TerracottaNodeList.class) {
            if (cached != null) {
                return cached;
            }
            List<String> result = new ArrayList<String>();
            try {
                String body = HttpUtil.get(TerracottaMetadata.NODE_LIST_URL);
                JsonElement parsed = new JsonParser().parse(body);
                if (parsed.isJsonArray()) {
                    JsonArray arr = parsed.getAsJsonArray();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonElement el = arr.get(i);
                        if (el.isJsonObject()) {
                            JsonObject node = el.getAsJsonObject();
                            if (node.has("url") && !node.get("url").isJsonNull()) {
                                result.add(node.get("url").getAsString());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Best-effort: an empty node list still lets Terracotta try its defaults.
                System.err.println("[Multiplayer164] Failed to fetch Terracotta node list: " + e.getMessage());
            }
            cached = Collections.unmodifiableList(result);
            return cached;
        }
    }
}
