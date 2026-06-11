package com.github.postyizhan.multiplayer164.terracotta;

import com.github.postyizhan.multiplayer164.util.ArchiveUtil;
import com.github.postyizhan.multiplayer164.util.HttpUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Handles installation of the Terracotta binary bundle: download the {@code .tar.gz}
 * (trying mirrors in order), verify its SHA-512, extract it into the per-version install
 * directory, and verify each extracted file's hash. The downloaded archive is the
 * unmodified upstream release (required by Terracotta's AGPL bundling exception).
 */
public final class TerracottaBundle {
    private final TerracottaMetadata.Bundle bundle;
    private final File installDir;

    public TerracottaBundle(TerracottaMetadata.Bundle bundle, File installDir) {
        this.bundle = bundle;
        this.installDir = installDir;
    }

    /** True if every expected file is present with a matching hash. */
    public boolean isInstalled() {
        try {
            for (TerracottaMetadata.FileEntry entry : bundle.files) {
                File f = new File(installDir, entry.name);
                if (!f.isFile() || !ArchiveUtil.sha512(f).equalsIgnoreCase(entry.sha512)) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Path to the launchable Terracotta executable inside the install dir. */
    public File executable() {
        return new File(installDir, bundle.executableName);
    }

    /**
     * Downloads (if needed), verifies and extracts the bundle. No-op when already
     * installed. Throws if all mirrors fail or verification fails.
     */
    public void ensureInstalled() throws IOException {
        if (isInstalled()) {
            return;
        }
        if (!installDir.exists() && !installDir.mkdirs()) {
            throw new IOException("Cannot create install dir: " + installDir);
        }

        File archive = new File(installDir, "terracotta-" + TerracottaMetadata.VERSION + "-" + bundle.classifier + "-pkg.tar.gz");
        downloadArchive(archive);

        String actual = ArchiveUtil.sha512(archive);
        if (!actual.equalsIgnoreCase(bundle.archiveSha512)) {
            archive.delete();
            throw new IOException("Terracotta archive SHA-512 mismatch (expected "
                    + bundle.archiveSha512 + ", got " + actual + ")");
        }

        ArchiveUtil.extractTarGz(archive, installDir);
        archive.delete();

        // Verify each extracted file.
        for (TerracottaMetadata.FileEntry entry : bundle.files) {
            File f = new File(installDir, entry.name);
            if (!f.isFile()) {
                throw new IOException("Missing extracted file: " + entry.name);
            }
            String h = ArchiveUtil.sha512(f);
            if (!h.equalsIgnoreCase(entry.sha512)) {
                throw new IOException("Extracted file SHA-512 mismatch: " + entry.name);
            }
        }
    }

    private void downloadArchive(File archive) throws IOException {
        List<String> urls = TerracottaMetadata.downloadUrls(bundle.classifier);
        IOException last = null;
        for (String url : urls) {
            OutputStream out = null;
            try {
                out = new FileOutputStream(archive);
                long bytes = HttpUtil.download(url, out);
                out.close();
                out = null;
                if (bytes > 0) {
                    return;
                }
            } catch (IOException e) {
                last = e;
                System.err.println("[Multiplayer164] Terracotta download failed from " + url + ": " + e.getMessage());
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        throw new IOException("All Terracotta download mirrors failed", last);
    }
}
