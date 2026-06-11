package com.github.postyizhan.multiplayer164.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;

/**
 * Minimal {@code .tar.gz} extractor and SHA-512 helper. Replaces commons-compress,
 * which ships as a Java 8 multi-release jar that the 1.6.4 (ASM4) remapper cannot
 * process. Only the small subset of the tar format produced by Terracotta's release
 * packages is supported (regular files + directories, USTAR/GNU headers).
 */
public final class ArchiveUtil {
    private static final int BLOCK = 512;

    private ArchiveUtil() {
    }

    /**
     * Extracts a gzip-compressed tar archive into {@code destDir}. Returns nothing;
     * throws on malformed entries or path traversal attempts.
     */
    public static void extractTarGz(File archive, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Cannot create directory: " + destDir);
        }
        String canonicalDest = destDir.getCanonicalPath();
        InputStream in = null;
        try {
            in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(archive)));
            byte[] header = new byte[BLOCK];
            while (true) {
                readFully(in, header, 0, BLOCK);
                if (isAllZero(header)) {
                    break; // end-of-archive marker
                }
                String name = parseString(header, 0, 100);
                if (name.isEmpty()) {
                    break;
                }
                long size = parseOctal(header, 124, 12);
                char type = (char) (header[156] & 0xff);

                File outFile = new File(destDir, name);
                if (!outFile.getCanonicalPath().startsWith(canonicalDest)) {
                    throw new IOException("Tar entry escapes destination: " + name);
                }

                if (type == '5' || name.endsWith("/")) {
                    outFile.mkdirs();
                    skipPadding(in, size);
                    continue;
                }
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                OutputStream out = new FileOutputStream(outFile);
                try {
                    copyExact(in, out, size);
                } finally {
                    out.close();
                }
                skipPadding(in, size);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /** Computes the lowercase hex SHA-512 of a file. */
    public static String sha512(File file) throws IOException {
        InputStream in = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            in = new BufferedInputStream(new FileInputStream(file));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                md.update(buf, 0, n);
            }
            return toHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-512 unavailable", e);
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private static void copyExact(InputStream in, OutputStream out, long size) throws IOException {
        byte[] buf = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buf.length, remaining);
            int n = in.read(buf, 0, toRead);
            if (n == -1) {
                throw new IOException("Unexpected EOF in tar entry");
            }
            out.write(buf, 0, n);
            remaining -= n;
        }
    }

    private static void skipPadding(InputStream in, long size) throws IOException {
        long mod = size % BLOCK;
        if (mod != 0) {
            long pad = BLOCK - mod;
            byte[] scratch = new byte[(int) pad];
            readFully(in, scratch, 0, (int) pad);
        }
    }

    private static void readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int n = in.read(b, off + read, len - read);
            if (n == -1) {
                throw new IOException("Unexpected EOF");
            }
            read += n;
        }
    }

    private static boolean isAllZero(byte[] b) {
        for (byte v : b) {
            if (v != 0) {
                return false;
            }
        }
        return true;
    }

    private static String parseString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) {
            end++;
        }
        try {
            return new String(b, off, end - off, "UTF-8").trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static long parseOctal(byte[] b, int off, int len) {
        long result = 0;
        int i = off;
        int max = off + len;
        // skip leading spaces / NUL
        while (i < max && (b[i] == ' ' || b[i] == 0)) {
            i++;
        }
        while (i < max && b[i] >= '0' && b[i] <= '7') {
            result = (result << 3) + (b[i] - '0');
            i++;
        }
        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) {
            int v = x & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString();
    }
}
