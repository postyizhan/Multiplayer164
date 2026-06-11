package com.github.postyizhan.multiplayer164.util;

import net.minecraft.util.StatCollector;

/**
 * Small i18n helper around vanilla {@link StatCollector}. Falls back to a supplied
 * default string when a translation key is missing (StatCollector returns the key
 * itself when unresolved), so the GUI stays readable even if the {@code .lang} files
 * are absent.
 */
public final class I18n {
    private I18n() {
    }

    public static String tr(String key, String fallback) {
        String value = StatCollector.translateToLocal(key);
        if (value == null || value.equals(key)) {
            return fallback;
        }
        return value;
    }

    public static String trf(String key, String fallback, Object... args) {
        String pattern = tr(key, fallback);
        try {
            return String.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }
}
