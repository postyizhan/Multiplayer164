package com.github.postyizhan.multiplayer164.terracotta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class TerracottaMetadataTest {
    @Test
    public void downloadUrls_substitutesVersionAndClassifier() {
        List<String> urls = TerracottaMetadata.downloadUrls("windows-x86_64");
        assertEquals(3, urls.size());
        for (String url : urls) {
            assertTrue("version substituted: " + url, url.contains("0.4.2"));
            assertTrue("classifier substituted: " + url, url.contains("windows-x86_64"));
            assertTrue("no leftover placeholder: " + url, !url.contains("${"));
        }
        // CN mirrors first (service is Chinese-Mainland only).
        assertTrue(urls.get(0).contains("gitee.com"));
        assertTrue(urls.get(2).contains("github.com"));
    }

    @Test
    public void windowsBundle_hasExecutableAndFiles() {
        TerracottaMetadata.Bundle b = TerracottaMetadata.currentBundleFor("windows-x86_64");
        assertNotNull(b);
        assertEquals("terracotta-0.4.2-windows-x86_64.exe", b.executableName);
        assertEquals(2, b.files.size());
    }

    @Test
    public void thirtyTwoBitNotMapped() {
        // currentClassifier reads the host, but classifier resolution for an unknown
        // key should yield no bundle.
        assertEquals(null, TerracottaMetadata.currentBundleFor("linux-x86_64"));
    }
}
