package com.github.postyizhan.multiplayer164.terracotta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

public class TerracottaStateTest {
    private static TerracottaState parse(String json) {
        JsonObject obj = new JsonParser().parse(json).getAsJsonObject();
        return TerracottaState.fromJson(obj);
    }

    @Test
    public void hostOk_parsesRoomCode() {
        TerracottaState s = parse("{\"state\":\"host-ok\",\"index\":3,\"room\":\"ABCD-1234\"}");
        assertEquals(TerracottaState.Kind.HOST_OK, s.kind);
        assertEquals(3, s.index);
        assertEquals("ABCD-1234", s.room);
        assertNull(s.url);
    }

    @Test
    public void guestOk_parsesBackupUrl() {
        TerracottaState s = parse("{\"state\":\"guest-ok\",\"index\":5,\"url\":\"mc://127.0.0.1:25571\"}");
        assertEquals(TerracottaState.Kind.GUEST_OK, s.kind);
        assertEquals("mc://127.0.0.1:25571", s.url);
    }

    @Test
    public void exception_parsesType() {
        TerracottaState s = parse("{\"state\":\"exception\",\"index\":1,\"type\":\"guest-et-crash\"}");
        assertTrue(s.isException());
        assertEquals("guest-et-crash", s.exceptionType);
    }

    @Test
    public void exception_parsesIntegerType_mapsToName() {
        // Terracotta sends `type` as the integer ordinal: 0 = ping-host-fail.
        TerracottaState s = parse("{\"state\":\"exception\",\"index\":1,\"type\":0}");
        assertTrue(s.isException());
        assertEquals("ping-host-fail", s.exceptionType);
    }

    @Test
    public void exception_parsesIntegerType_guestCrash() {
        TerracottaState s = parse("{\"state\":\"exception\",\"index\":2,\"type\":2}");
        assertEquals("guest-et-crash", s.exceptionType);
    }

    @Test
    public void exception_outOfRangeIntegerType_isLabelled() {
        TerracottaState s = parse("{\"state\":\"exception\",\"index\":2,\"type\":99}");
        assertEquals("unknown-99", s.exceptionType);
    }

    @Test
    public void unknownState_mapsToUnknown() {
        TerracottaState s = parse("{\"state\":\"something-new\",\"index\":0}");
        assertEquals(TerracottaState.Kind.UNKNOWN, s.kind);
    }

    @Test
    public void missingIndex_defaultsToNegativeOne() {
        TerracottaState s = parse("{\"state\":\"waiting\"}");
        assertEquals(TerracottaState.Kind.WAITING, s.kind);
        assertEquals(-1, s.index);
    }
}
