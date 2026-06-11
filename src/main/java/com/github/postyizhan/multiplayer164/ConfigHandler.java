package com.github.postyizhan.multiplayer164;

import java.io.File;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;

public class ConfigHandler {
    public static Configuration config;
    
    public static int lastPort = 25565;
    public static boolean lastPvp = true;
    public static String lastGameMode = "survival";
    public static boolean lastAllowCommands = false;
    /** Display name used when hosting/joining a Terracotta room (empty = use MC username). */
    public static String lastPlayerName = "";

    public static void init(File configFile) {
        config = new Configuration(configFile);
        config.load();

        loadSettings();

        config.save();
    }

    public static void loadSettings() {
        lastPort = config.get(Configuration.CATEGORY_GENERAL, "lastPort", 25565, "Last used LAN port").getInt(25565);
        lastPvp = config.get(Configuration.CATEGORY_GENERAL, "lastPvp", true, "Last used PVP setting").getBoolean(true);
        lastGameMode = config.get(Configuration.CATEGORY_GENERAL, "lastGameMode", "survival", "Last used Game Mode").getString();
        lastAllowCommands = config.get(Configuration.CATEGORY_GENERAL, "lastAllowCommands", false, "Last used Allow Commands setting").getBoolean(false);
        lastPlayerName = config.get(Configuration.CATEGORY_GENERAL, "lastPlayerName", "", "Display name for multiplayer rooms (empty = use Minecraft username)").getString();
    }
    
    public static void saveSettings(int port, boolean pvp, String gameMode, boolean allowCommands) {
        lastPort = port;
        lastPvp = pvp;
        lastGameMode = gameMode;
        lastAllowCommands = allowCommands;
        
        config.get(Configuration.CATEGORY_GENERAL, "lastPort", 25565).set(port);
        config.get(Configuration.CATEGORY_GENERAL, "lastPvp", true).set(pvp);
        config.get(Configuration.CATEGORY_GENERAL, "lastGameMode", "survival").set(gameMode);
        config.get(Configuration.CATEGORY_GENERAL, "lastAllowCommands", false).set(allowCommands);
        
        config.save();
    }
}
