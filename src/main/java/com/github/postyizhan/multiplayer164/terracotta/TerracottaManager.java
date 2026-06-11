package com.github.postyizhan.multiplayer164.terracotta;

import java.io.File;
import java.util.List;

/**
 * Single entry point for the UI layer. Owns the Terracotta install/launch/poll
 * lifecycle and exposes high-level {@code host}/{@code join} operations plus the
 * latest observed {@link TerracottaState}. All long-running work happens on background
 * threads; callers supply a {@link Callback} to receive results on those threads
 * (the UI is responsible for marshalling back to the game thread).
 *
 * <p>This is an independent reimplementation of HMCL's orchestration against the same
 * Terracotta HTTP contract — no HMCL (GPLv3) code is reused.
 */
public final class TerracottaManager {
    /** Receives the outcome of an async host/join request. */
    public interface Callback {
        void onState(TerracottaState state);

        void onError(String message);
    }

    private static final TerracottaManager INSTANCE = new TerracottaManager();

    public static TerracottaManager getInstance() {
        return INSTANCE;
    }

    private File gameDir;
    private TerracottaProcess process;
    private TerracottaClient client;
    private volatile TerracottaState lastState;
    private volatile boolean starting;

    private TerracottaManager() {
    }

    /** Must be called once during mod init with the game directory. */
    public synchronized void init(File gameDir) {
        this.gameDir = gameDir;
    }

    public TerracottaState getLastState() {
        return lastState;
    }

    /** True if the current platform is supported by this mod (Windows only here). */
    public boolean isPlatformSupported() {
        return TerracottaMetadata.currentBundle() != null;
    }

    /**
     * Ensures Terracotta is installed and running, then starts hosting (LAN scan).
     * The host must already have opened a world to LAN. Runs asynchronously.
     */
    public void host(final String playerName, final Callback callback) {
        runAsync(new Runnable() {
            public void run() {
                try {
                    TerracottaClient c = ensureRunning();
                    c.startScanning(playerName, TerracottaNodeList.fetch());
                    TerracottaState result = await(c, hostTargetKinds(), callback);
                    if (result != null) {
                        callback.onState(result);
                    }
                } catch (Exception e) {
                    callback.onError(describe(e));
                }
            }
        });
    }

    /** Ensures Terracotta is installed and running, then joins {@code room}. Async. */
    public void join(final String room, final String playerName, final Callback callback) {
        runAsync(new Runnable() {
            public void run() {
                try {
                    TerracottaClient c = ensureRunning();
                    c.startGuesting(room, playerName, TerracottaNodeList.fetch());
                    TerracottaState result = await(c, guestTargetKinds(), callback);
                    if (result != null) {
                        callback.onState(result);
                    }
                } catch (Exception e) {
                    callback.onError(describe(e));
                }
            }
        });
    }

    /** Returns Terracotta to idle (closes the current room/connection). Async, best-effort. */
    public void reset() {
        runAsync(new Runnable() {
            public void run() {
                try {
                    if (client != null) {
                        client.setIdle();
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private synchronized TerracottaClient ensureRunning() throws Exception {
        if (gameDir == null) {
            throw new IllegalStateException("TerracottaManager not initialized");
        }
        TerracottaMetadata.Bundle bundle = TerracottaMetadata.currentBundle();
        if (bundle == null) {
            throw new UnsupportedOperationException("platform-unsupported");
        }
        // Reuse a live instance. We can't rely on process.isRunning() alone: Terracotta
        // elevates (UAC) into a separate child to create EasyTier's TUN adapter, which
        // orphans the parent handle we spawned. If the HTTP API still answers, the core is
        // up — relaunching would only fire another UAC prompt and churn the global mutex.
        if (client != null && client.ping()) {
            return client;
        }
        if (client != null && process != null && process.isRunning()) {
            return client;
        }
        if (starting) {
            throw new IllegalStateException("Terracotta is already starting");
        }
        starting = true;
        try {
            File installDir = TerracottaMetadata.installDir(gameDir);
            TerracottaBundle tb = new TerracottaBundle(bundle, installDir);
            tb.ensureInstalled();
            process = new TerracottaProcess(tb.executable());
            int port = process.start();
            client = new TerracottaClient(port);
            return client;
        } finally {
            starting = false;
        }
    }

    /**
     * Polls {@code /state} until the state reaches one of {@code targets} or an
     * exception state. Returns the terminal state. Intermediate states are reported
     * via {@link Callback#onState} so the UI can show progress.
     */
    private TerracottaState await(TerracottaClient c, TerracottaState.Kind[] targets, Callback callback) throws Exception {
        long deadline = System.currentTimeMillis() + 120000;
        TerracottaState.Kind lastKind = null;
        while (System.currentTimeMillis() < deadline) {
            TerracottaState s = c.pollState();
            lastState = s;
            if (s.kind != lastKind) {
                lastKind = s.kind;
                callback.onState(s); // progress update
            }
            if (s.isException()) {
                return s;
            }
            for (TerracottaState.Kind target : targets) {
                if (s.kind == target) {
                    return s;
                }
            }
            Thread.sleep(500);
        }
        throw new java.io.IOException("Timed out waiting for Terracotta to reach the target state");
    }

    private static TerracottaState.Kind[] hostTargetKinds() {
        return new TerracottaState.Kind[]{TerracottaState.Kind.HOST_OK};
    }

    private static TerracottaState.Kind[] guestTargetKinds() {
        return new TerracottaState.Kind[]{TerracottaState.Kind.GUEST_OK};
    }

    private static String describe(Exception e) {
        if (e instanceof UnsupportedOperationException && "platform-unsupported".equals(e.getMessage())) {
            return "platform-unsupported";
        }
        if (e instanceof com.github.postyizhan.multiplayer164.util.HttpStatusException) {
            // Stable, body-free token the UI can translate (e.g. "http-400").
            return "http-" + ((com.github.postyizhan.multiplayer164.util.HttpStatusException) e).getStatusCode();
        }
        if (e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException) {
            return "connect-failed";
        }
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    private static void runAsync(Runnable r) {
        Thread t = new Thread(r, "Terracotta-Manager");
        t.setDaemon(true);
        t.start();
    }
}
