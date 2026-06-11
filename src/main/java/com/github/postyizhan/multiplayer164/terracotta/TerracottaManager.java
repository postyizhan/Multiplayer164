package com.github.postyizhan.multiplayer164.terracotta;

import java.io.File;
import java.io.IOException;
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

    private enum SessionMode {
        IDLE,
        HOST,
        GUEST
    }

    private static final TerracottaManager INSTANCE = new TerracottaManager();
    private static final long RESET_WAIT_MS = 5000;
    private static final long RESET_POLL_MS = 200;

    public static TerracottaManager getInstance() {
        return INSTANCE;
    }

    private File gameDir;
    private volatile TerracottaProcess process;
    private volatile TerracottaClient client;
    private volatile TerracottaState lastState;
    private volatile boolean starting;
    private volatile SessionMode sessionMode = SessionMode.IDLE;
    /**
     * Incremented every time the lifecycle changes. Async host/join loops capture the
     * value and silently stop if a later reset/new operation supersedes them.
     */
    private volatile int lifecycleVersion;
    private boolean shutdownHookRegistered;

    private TerracottaManager() {
    }

    /** Must be called once during mod init with the game directory. */
    public synchronized void init(File gameDir) {
        this.gameDir = gameDir;
        if (!shutdownHookRegistered) {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    shutdownNow();
                }
            }, "Terracotta-Shutdown"));
            shutdownHookRegistered = true;
        }
    }

    public TerracottaState getLastState() {
        return lastState;
    }

    /** True if a host/join session is expected to be open right now. */
    public boolean hasActiveSession() {
        return sessionMode != SessionMode.IDLE;
    }

    /** True if this manager has ever started or attached to a Terracotta core. */
    public boolean hasKnownCore() {
        return client != null || process != null;
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
        final int version = beginSession(SessionMode.HOST);
        runAsync(new Runnable() {
            public void run() {
                try {
                    TerracottaClient c = prepareForNewSession();
                    if (!isCurrent(version)) {
                        return;
                    }
                    c.startScanning(playerName, TerracottaNodeList.fetch());
                    TerracottaState result = await(c, hostTargetKinds(), callback, version);
                    if (result != null && isCurrent(version)) {
                        if (result.isException()) {
                            endSessionIfCurrent(version);
                        }
                        callback.onState(result);
                    }
                } catch (Exception e) {
                    if (isCurrent(version)) {
                        endSessionIfCurrent(version);
                        callback.onError(describe(e));
                    }
                }
            }
        });
    }

    /** Ensures Terracotta is installed and running, then joins {@code room}. Async. */
    public void join(final String room, final String playerName, final Callback callback) {
        final int version = beginSession(SessionMode.GUEST);
        runAsync(new Runnable() {
            public void run() {
                try {
                    TerracottaClient c = prepareForNewSession();
                    if (!isCurrent(version)) {
                        return;
                    }
                    c.startGuesting(room, playerName, TerracottaNodeList.fetch());
                    TerracottaState result = await(c, guestTargetKinds(), callback, version);
                    if (result != null && isCurrent(version)) {
                        if (result.isException()) {
                            endSessionIfCurrent(version);
                        }
                        callback.onState(result);
                    }
                } catch (Exception e) {
                    if (isCurrent(version)) {
                        endSessionIfCurrent(version);
                        callback.onError(describe(e));
                    }
                }
            }
        });
    }

    /**
     * Returns Terracotta to idle (closes the current room/connection). Async,
     * best-effort. The core process is kept alive for reuse to avoid another UAC prompt.
     */
    public void reset() {
        final int version = cancelSession();
        if (!hasKnownCore()) {
            return;
        }
        runAsync(new Runnable() {
            public void run() {
                resetNow(false, version);
            }
        });
    }

    /** Synchronous shutdown used by the JVM shutdown hook. */
    private void shutdownNow() {
        cancelSession();
        resetNow(true, -1);
    }

    private synchronized int beginSession(SessionMode mode) {
        lifecycleVersion++;
        sessionMode = mode;
        lastState = null;
        return lifecycleVersion;
    }

    private synchronized int cancelSession() {
        lifecycleVersion++;
        sessionMode = SessionMode.IDLE;
        lastState = null;
        return lifecycleVersion;
    }

    private synchronized void endSessionIfCurrent(int version) {
        if (lifecycleVersion == version) {
            sessionMode = SessionMode.IDLE;
        }
    }

    private boolean isCurrent(int version) {
        return lifecycleVersion == version;
    }

    /**
     * A new host/join must always start from a clean Terracotta state. If an old core is
     * still alive in HOST_OK/GUEST_OK/EXCEPTION, reset it first; if that fails, discard
     * and relaunch so the next operation cannot inherit a dirty room/connection.
     */
    private TerracottaClient prepareForNewSession() throws Exception {
        TerracottaClient c = ensureRunning();
        if (resetToIdle(c, RESET_WAIT_MS)) {
            return c;
        }

        discardCore();
        c = ensureRunning();
        if (!resetToIdle(c, RESET_WAIT_MS)) {
            throw new IOException("Unable to reset Terracotta to idle");
        }
        return c;
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
        // up — but prepareForNewSession() will still force it back to idle before use.
        if (client != null && client.ping()) {
            return client;
        }

        // A process handle without a responsive HTTP API is not useful. Stop it and let
        // TerracottaProcess.start() clean any same-named strays before launching anew.
        if (process != null) {
            process.stop();
            process = null;
        }
        client = null;

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

    /** Drops the cached client/process so the next ensureRunning() performs a clean start. */
    private synchronized void discardCore() {
        if (process != null) {
            process.stop();
        }
        process = null;
        client = null;
        lastState = null;
    }

    /** Best-effort reset, or full core shutdown when stopProcess is true. */
    private void resetNow(boolean stopProcess, int expectedVersion) {
        if (expectedVersion >= 0 && !isCurrent(expectedVersion)) {
            return;
        }

        TerracottaClient c = client;
        TerracottaProcess p = process;
        if (c != null) {
            if (stopProcess) {
                try {
                    // On Windows the real Terracotta core can be a detached/elevated
                    // child process. Process.destroy() only reaches the launcher we
                    // spawned, so use Terracotta's own API to terminate the actual core.
                    c.shutdown();
                } catch (Exception ignored) {
                    // Fall back below to killing same-named processes if possible.
                }
            } else {
                resetToIdle(c, Math.min(RESET_WAIT_MS, 3000));
            }
        }
        if (stopProcess && p != null) {
            p.stopAll();
        }
        if (stopProcess) {
            synchronized (this) {
                if (client == c) {
                    client = null;
                }
                if (process == p) {
                    process = null;
                }
                lastState = null;
            }
        }
    }

    /** Calls /state/ide and waits briefly until /state reports the WAITING state. */
    private boolean resetToIdle(TerracottaClient c, long waitMs) {
        try {
            c.setIdle();
        } catch (Exception e) {
            return false;
        }

        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                TerracottaState s = c.pollState();
                lastState = s;
                if (s.kind == TerracottaState.Kind.WAITING) {
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
            sleep(RESET_POLL_MS);
        }
        return false;
    }

    /**
     * Polls {@code /state} until the state reaches one of {@code targets} or an
     * exception state. Returns the terminal state. Intermediate states are reported
     * via {@link Callback#onState} so the UI can show progress.
     */
    private TerracottaState await(TerracottaClient c, TerracottaState.Kind[] targets,
                                  Callback callback, int version) throws Exception {
        long deadline = System.currentTimeMillis() + 120000;
        TerracottaState.Kind lastKind = null;
        while (System.currentTimeMillis() < deadline) {
            if (!isCurrent(version)) {
                return null;
            }

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
        throw new IOException("Timed out waiting for Terracotta to reach the target state");
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

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
