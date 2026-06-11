package com.github.postyizhan.multiplayer164;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = Multiplayer164.MODID, version = Multiplayer164.VERSION)
public class Multiplayer164 {
    public static final String MODID = "Multiplayer164";
    public static final String VERSION = "1.0";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Initialize Config
        ConfigHandler.init(event.getSuggestedConfigurationFile());

        // Initialize the Terracotta multiplayer manager with the game directory.
        // The config dir is <gameDir>/config, so its parent is the game directory.
        com.github.postyizhan.multiplayer164.terracotta.TerracottaManager.getInstance()
                .init(event.getModConfigurationDirectory().getParentFile());

        // Register event handlers for the custom GUIs.
        MinecraftForge.EVENT_BUS.register(new LanGuiHandler());
        MinecraftForge.EVENT_BUS.register(new MultiplayerGuiHandler());
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Register player tracker for team assignment
        GameRegistry.registerPlayerTracker(new PlayerTracker());
    }
}
