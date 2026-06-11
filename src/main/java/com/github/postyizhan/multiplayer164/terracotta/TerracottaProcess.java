package com.github.postyizhan.multiplayer164.terracotta;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches the Terracotta executable as a subprocess and discovers the local HTTP
 * port it binds. Terracotta is started as {@code terracotta.exe --hmcl <portFile>};
 * it writes a small JSON file {@code {"port": <n>}} to {@code portFile} once its HTTP
 * server is listening. The child process is killed when the JVM exits.
 */
public final class TerracottaProcess {
    private static final long PORT_WAIT_MS = 30000;
    private static final long POLL_INTERVAL_MS = 200;

    private final File executable;
    private Process process;
    private int port = -1;

    public TerracottaProcess(File executable) {
        this.executable = executable;
    }

    public boolean isRunning() {
        if (process == null) {
            return false;
        }
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true; // still running
        }
    }

    public int getPort() {
        return port;
    }

    /**
     * Starts the process and blocks until the port file appears (or timeout). Returns
     * the bound port. Throws if the process cannot start or never reports a port.
     */
    public synchronized int start() throws IOException {
        if (isRunning() && port > 0) {
            return port;
        }
        // Terracotta is a singleton (global mutex). A stale instance left over from a
        // previous run would force this one into "secondary mode" and we could end up
        // talking to a dirty/dead holder. Kill any same-named strays first so we always
        // get a clean primary that we own.
        killStrayProcesses();

        File portFile = File.createTempFile("multiplayer164-terracotta", ".port");
        portFile.delete(); // Terracotta creates it; we just need a unique path

        List<String> command = new ArrayList<String>();
        command.add(executable.getAbsolutePath());
        command.add("--hmcl");
        command.add(portFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(executable.getParentFile());
        pb.redirectErrorStream(true);
        process = pb.start();
        drainAsync(process.getInputStream());
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                stop();
            }
        }));

        long deadline = System.currentTimeMillis() + PORT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            // Read the port file FIRST: Terracotta is a singleton. When another instance
            // already holds the global mutex, this process runs in "secondary mode" — it
            // writes the (shared) port and then exits immediately. So a produced port file
            // is valid even if the process has already exited.
            if (portFile.isFile() && portFile.length() > 0) {
                int p = readPort(portFile);
                if (p > 0) {
                    this.port = p;
                    portFile.delete();
                    return p;
                }
            }
            if (!isRunning()) {
                // Give the port file one last chance (it may have been written just before exit).
                if (portFile.isFile() && portFile.length() > 0) {
                    int p = readPort(portFile);
                    if (p > 0) {
                        this.port = p;
                        portFile.delete();
                        return p;
                    }
                }
                throw new IOException("Terracotta process exited before reporting a port");
            }
            sleep(POLL_INTERVAL_MS);
        }
        stop();
        throw new IOException("Timed out waiting for Terracotta to report its port");
    }

    public synchronized void stop() {
        if (process != null) {
            process.destroy();
            process = null;
            port = -1;
        }
    }

    /**
     * Best-effort kill of any stray Terracotta processes with the same executable name,
     * left over from a previous run. Prevents the singleton mutex from forcing us into
     * secondary mode against a dirty holder. Failures are ignored.
     */
    private void killStrayProcesses() {
        String exeName = executable.getName();
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("taskkill", "/F", "/IM", exeName);
            } else {
                pb = new ProcessBuilder("pkill", "-f", exeName);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Drain output so the process can exit, then wait briefly.
            InputStream in = p.getInputStream();
            byte[] scratch = new byte[256];
            while (in.read(scratch) != -1) {
                // discard
            }
            p.waitFor();
            // Give the OS a moment to release the mutex/port before we launch our own.
            sleep(300);
        } catch (Exception e) {
            // No strays, or the kill tool is unavailable — nothing to do.
        }
    }

    private static int readPort(File portFile) {
        try {
            byte[] buf = new byte[(int) portFile.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(portFile);
            try {
                int read = 0;
                while (read < buf.length) {
                    int n = fis.read(buf, read, buf.length - read);
                    if (n == -1) {
                        break;
                    }
                    read += n;
                }
            } finally {
                fis.close();
            }
            String text = new String(buf, "UTF-8").trim();
            JsonObject obj = new JsonParser().parse(text).getAsJsonObject();
            if (obj.has("port")) {
                return obj.get("port").getAsInt();
            }
        } catch (Exception e) {
            // not ready yet / partial write
        }
        return -1;
    }

    private static void drainAsync(final InputStream in) {
        Thread t = new Thread(new Runnable() {
            public void run() {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Terracotta] " + line);
                    }
                } catch (IOException ignored) {
                } finally {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "Terracotta-Output");
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
