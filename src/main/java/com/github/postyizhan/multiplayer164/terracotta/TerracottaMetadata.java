package com.github.postyizhan.multiplayer164.terracotta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static metadata describing the Terracotta multiplayer binary: its version, the
 * per-platform download bundle (file names + SHA-512 hashes), and the download URL
 * templates. Values mirror HMCL's machine-generated {@code terracotta.json}
 * (data, not code — see the multiplayer feature notes). The binary itself is the
 * unmodified upstream release from github.com/burningtnt/Terracotta (AGPL-3.0 with
 * an IPC/binary-bundling exception).
 */
public final class TerracottaMetadata {
    public static final String VERSION = "0.4.2";

    public static final String COPYRIGHT_NOTICE =
            "Terracotta (陶瓦联机) — © burningtnt, AGPL-3.0. "
                    + "https://github.com/burningtnt/Terracotta";

    /** Public coordination node list (region-aware; service is Chinese-Mainland only). */
    public static final String NODE_LIST_URL = "https://terracotta.glavo.site/nodes";

    /** A single downloadable file inside a platform bundle. */
    public static final class FileEntry {
        public final String name;
        public final String sha512;

        FileEntry(String name, String sha512) {
            this.name = name;
            this.sha512 = sha512;
        }
    }

    /** A platform bundle: the archive hash plus the files it should contain. */
    public static final class Bundle {
        public final String classifier;
        public final String archiveSha512;
        public final List<FileEntry> files;
        public final String executableName;

        Bundle(String classifier, String archiveSha512, List<FileEntry> files, String executableName) {
            this.classifier = classifier;
            this.archiveSha512 = archiveSha512;
            this.files = Collections.unmodifiableList(files);
            this.executableName = executableName;
        }
    }

    private static final Map<String, Bundle> BUNDLES = new LinkedHashMap<String, Bundle>();

    static {
        // windows-x86_64
        List<FileEntry> winX64 = new ArrayList<FileEntry>();
        winX64.add(new FileEntry("VCRUNTIME140.DLL",
                "3d4b24061f72c0e957c7b04a0c4098c94c8f1afb4a7e159850b9939c7210d73398be6f27b5ab85073b4e8c999816e7804fef0f6115c39cd061f4aaeb4dcda8cf"));
        winX64.add(new FileEntry("terracotta-0.4.2-windows-x86_64.exe",
                "6e98d1f2380ed22fb5a2dd4aafce6c773e9cf69100c8bb8e49e7d6983756bdb9a31f80e06bcfbe5a2742144fe806d3d687dec54d8f09d87c659341f99dd9fd80"));
        BUNDLES.put("windows-x86_64", new Bundle("windows-x86_64",
                "6a98f524d4f00373696517306af8aa50d01d55ce4eadb27e9e4bc2f882707a0b5f20d5d4c33371d1459dcf5bf144ffed9beb414202d9ccf32b11dbbfcf19d650",
                winX64, "terracotta-0.4.2-windows-x86_64.exe"));

        // windows-arm64
        List<FileEntry> winArm = new ArrayList<FileEntry>();
        winArm.add(new FileEntry("VCRUNTIME140.DLL",
                "5cb5ce114614101d260f4754c09e8a0dd57e4da885ebb96b91e274326f3e1dd95ed0ade9f542f1922fad0ed025e88a1f368e791e1d01fae69718f0ec3c7b98c8"));
        winArm.add(new FileEntry("terracotta-0.4.2-windows-arm64.exe",
                "30a15c5c53e5817c5a3634532172559327474741d3b2c7ef4e8a30acc6f59cdcf3570bf5f583e3cbe9e2abc8253e977c1abda1e9f36c88c4e99240da257347d0"));
        BUNDLES.put("windows-arm64", new Bundle("windows-arm64",
                "fc1077247014ac0c712469498bde2ef7f6d881d5fcb7bdd5e11ebe20218fed365be19afdb8d453a79d77b729f866058522b910741767f4df947faa891434b463",
                winArm, "terracotta-0.4.2-windows-arm64.exe"));
    }

    private TerracottaMetadata() {
    }

    /**
     * Resolves the platform classifier for the current machine, e.g. {@code windows-x86_64}.
     * Returns {@code null} if the platform is unsupported by this mod (only Windows is
     * shipped here).
     */
    public static String currentClassifier() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (!os.contains("win")) {
            return null;
        }
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "windows-arm64";
        }
        if (arch.contains("64")) {
            return "windows-x86_64";
        }
        return null; // 32-bit unsupported
    }

    public static Bundle currentBundle() {
        String classifier = currentClassifier();
        return classifier == null ? null : BUNDLES.get(classifier);
    }

    /** Looks up the bundle for an explicit classifier (null if none). Test-friendly. */
    public static Bundle currentBundleFor(String classifier) {
        return classifier == null ? null : BUNDLES.get(classifier);
    }

    /**
     * Ordered list of download URLs for the given classifier. CN mirrors are placed
     * first because the Terracotta service is Chinese-Mainland only.
     */
    public static List<String> downloadUrls(String classifier) {
        List<String> urls = new ArrayList<String>();
        urls.add(template("https://gitee.com/burningtnt/Terracotta/releases/download/v${version}/terracotta-${version}-${classifier}-pkg.tar.gz", classifier));
        urls.add(template("https://cnb.cool/HMCL-Terracotta/Terracotta/-/releases/download/v${version}/terracotta-${version}-${classifier}-pkg.tar.gz", classifier));
        urls.add(template("https://github.com/burningtnt/Terracotta/releases/download/v${version}/terracotta-${version}-${classifier}-pkg.tar.gz", classifier));
        return urls;
    }

    /** Local install directory for the resolved Terracotta version under the game dir. */
    public static File installDir(File gameDir) {
        return new File(new File(new File(gameDir, "Multiplayer164"), "terracotta"), VERSION);
    }

    private static String template(String s, String classifier) {
        return s.replace("${version}", VERSION).replace("${classifier}", classifier);
    }
}
