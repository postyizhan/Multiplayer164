package com.github.postyizhan.multiplayer164;

import com.github.postyizhan.multiplayer164.terracotta.TerracottaManager;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.TickRegistry;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = Multiplayer164.MODID, version = Multiplayer164.VERSION)
public class Multiplayer164 {
    public static final String MODID = "Multiplayer164";
    public static final String VERSION = "2.0";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        // Initialize Config
        ConfigHandler.init(event.getSuggestedConfigurationFile());

        // Initialize the Terracotta multiplayer manager with the game directory.
        // The config dir is <gameDir>/config, so its parent is the game directory.
        TerracottaManager.getInstance().init(event.getModConfigurationDirectory().getParentFile());

        // Register client-side event handlers for the custom GUIs and world lifecycle.
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new LanGuiHandler());
            MinecraftForge.EVENT_BUS.register(new MultiplayerGuiHandler());
            TickRegistry.registerTickHandler(new TerracottaClientLifecycle(), Side.CLIENT);
        }
    }
    
    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        // Integrated server/world is closing: close any public Terracotta room so the
        // next host/join starts from a clean core state instead of the old HOST_OK room.
        TerracottaManager.getInstance().reset();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        // Register player tracker for team assignment
        GameRegistry.registerPlayerTracker(new PlayerTracker());
    }
}
