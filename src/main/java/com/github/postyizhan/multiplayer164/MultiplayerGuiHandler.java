package com.github.postyizhan.multiplayer164;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.ForgeSubscribe;

/**
 * Replaces the vanilla multiplayer server list with {@link GuiCustomMultiplayer},
 * which adds the "Join multiplayer room" entry point. Mirrors the approach used by
 * {@link LanGuiHandler} for the share-to-LAN screen.
 */
public class MultiplayerGuiHandler {
    @ForgeSubscribe
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiMultiplayer && !(event.gui instanceof GuiCustomMultiplayer)) {
            GuiScreen parent = readParent((GuiMultiplayer) event.gui);
            event.gui = new GuiCustomMultiplayer(parent);
        }
    }

    /**
     * Reads the vanilla GuiMultiplayer's private {@code parentScreen} so "Back" returns
     * to wherever the player came from (main menu or in-game pause menu). Falls back to
     * a fresh main menu if reflection fails.
     */
    private static GuiScreen readParent(GuiMultiplayer gui) {
        try {
            Field f = findField(GuiMultiplayer.class, "parentScreen", "field_74033_a");
            if (f != null) {
                f.setAccessible(true);
                Object value = f.get(gui);
                if (value instanceof GuiScreen) {
                    return (GuiScreen) value;
                }
            }
        } catch (Exception e) {
            // fall through
        }
        return new GuiMainMenu();
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
