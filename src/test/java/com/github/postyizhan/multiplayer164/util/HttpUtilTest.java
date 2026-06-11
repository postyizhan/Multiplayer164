package com.github.postyizhan.multiplayer164.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HttpUtilTest {
    @Test
    public void withQuery_encodesAndJoinsPairs() {
        List<String[]> params = new ArrayList<String[]>();
        params.add(new String[]{"player", "Steve Jobs"});
        params.add(new String[]{"room", "ABC&D"});
        String result = HttpUtil.withQuery("http://127.0.0.1:8080/state/guesting", params);
        assertEquals("http://127.0.0.1:8080/state/guesting?player=Steve+Jobs&room=ABC%26D", result);
    }

    @Test
    public void withQuery_appendsWhenBaseHasQuery() {
        List<String[]> params = new ArrayList<String[]>();
        params.add(new String[]{"fetch", "true"});
        String result = HttpUtil.withQuery("http://127.0.0.1:8080/log?x=1", params);
        assertTrue(result.startsWith("http://127.0.0.1:8080/log?x=1&fetch=true"));
    }

    @Test
    public void withQuery_handlesMultiplePublicNodes() {
        List<String[]> params = new ArrayList<String[]>();
        params.add(new String[]{"public_nodes", "node1"});
        params.add(new String[]{"public_nodes", "node2"});
        String result = HttpUtil.withQuery("http://x/scanning", params);
        assertEquals("http://x/scanning?public_nodes=node1&public_nodes=node2", result);
    }

    @Test
    public void get_on400_throwsTypedStatusException_withoutBody() throws Exception {
        final String htmlBody = "<!DOCTYPE html><html><head><title>400 Bad Request</title></head><body>oops</body></html>";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/state", new HttpHandler() {
            public void handle(HttpExchange ex) throws java.io.IOException {
                byte[] bytes = htmlBody.getBytes("UTF-8");
                ex.sendResponseHeaders(400, bytes.length);
                OutputStream os = ex.getResponseBody();
                os.write(bytes);
                os.close();
            }
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            HttpUtil.get("http://127.0.0.1:" + port + "/state");
            fail("expected HttpStatusException");
        } catch (HttpStatusException e) {
            assertEquals(400, e.getStatusCode());
            // The raw HTML body must never leak into the exception message (it was
            // being dumped into the in-game GUI as red text).
            assertFalse(e.getMessage().contains("DOCTYPE"));
            assertFalse(e.getMessage().contains("Bad Request"));
        } finally {
            server.stop(0);
        }
    }
}
