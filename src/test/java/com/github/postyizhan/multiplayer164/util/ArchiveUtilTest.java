package com.github.postyizhan.multiplayer164.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ArchiveUtilTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void extractsRegularFileFromTarGz() throws Exception {
        byte[] payload = "hello terracotta".getBytes("UTF-8");
        File archive = tmp.newFile("pkg.tar.gz");
        writeTarGz(archive, "terracotta.exe", payload);

        File dest = tmp.newFolder("out");
        ArchiveUtil.extractTarGz(archive, dest);

        File extracted = new File(dest, "terracotta.exe");
        assertTrue(extracted.isFile());
        assertEquals(payload.length, extracted.length());
    }

    @Test
    public void sha512_isStableLowercaseHex() throws Exception {
        File f = tmp.newFile("data.bin");
        OutputStream out = new FileOutputStream(f);
        out.write("abc".getBytes("UTF-8"));
        out.close();
        // Known SHA-512 of "abc"
        String expected = "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a"
                + "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f";
        assertEquals(expected, ArchiveUtil.sha512(f));
    }

    /** Writes a minimal single-file USTAR tar wrapped in gzip. */
    private static void writeTarGz(File archive, String name, byte[] content) throws Exception {
        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        byte[] header = new byte[512];
        byte[] nameBytes = name.getBytes("UTF-8");
        System.arraycopy(nameBytes, 0, header, 0, nameBytes.length);
        // mode, uid, gid
        putOctal(header, 100, 8, 0644);
        putOctal(header, 108, 8, 0);
        putOctal(header, 116, 8, 0);
        // size
        putOctal(header, 124, 12, content.length);
        // mtime
        putOctal(header, 136, 12, 0);
        header[156] = '0'; // regular file
        // ustar magic
        byte[] magic = "ustar".getBytes("UTF-8");
        System.arraycopy(magic, 0, header, 257, magic.length);
        // checksum: spaces then compute
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        int sum = 0;
        for (byte b : header) {
            sum += (b & 0xff);
        }
        putOctal(header, 148, 8, sum);
        header[154] = 0;
        header[155] = ' ';

        tar.write(header);
        tar.write(content);
        int pad = (512 - (content.length % 512)) % 512;
        tar.write(new byte[pad]);
        tar.write(new byte[1024]); // two zero blocks = end of archive

        OutputStream gz = new GZIPOutputStream(new FileOutputStream(archive));
        gz.write(tar.toByteArray());
        gz.close();
    }

    private static void putOctal(byte[] b, int off, int len, long value) {
        String s = Long.toOctalString(value);
        // right-justified, NUL-terminated field
        int pad = len - 1 - s.length();
        int i = off;
        for (int k = 0; k < pad; k++) {
            b[i++] = '0';
        }
        for (int k = 0; k < s.length(); k++) {
            b[i++] = (byte) s.charAt(k);
        }
        b[off + len - 1] = 0;
    }
}
