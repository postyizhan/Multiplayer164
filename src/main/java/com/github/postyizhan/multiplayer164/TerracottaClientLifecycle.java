package com.github.postyizhan.multiplayer164;

import com.github.postyizhan.multiplayer164.terracotta.TerracottaManager;

import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import net.minecraft.client.Minecraft;

import java.util.EnumSet;

/**
 * Watches the client world lifecycle and closes any active Terracotta room/connection
 * after the player leaves a world or disconnects from a server. Forge 1.6.4 does not
 * have the newer client-disconnect events, so a CLIENT tick transition is the most
 * reliable client-side hook here.
 */
public final class TerracottaClientLifecycle implements ITickHandler {
    private boolean hadWorld;

    public void tickStart(EnumSet<TickType> type, Object... tickData) {
        // Nothing to do at tick start.
    }

    public void tickEnd(EnumSet<TickType> type, Object... tickData) {
        Minecraft mc = Minecraft.getMinecraft();
        boolean hasWorld = mc != null && mc.theWorld != null;

        if (hadWorld && !hasWorld) {
            TerracottaManager manager = TerracottaManager.getInstance();
            if (manager.hasActiveSession() || manager.hasKnownCore()) {
                manager.reset();
            }
        }
        hadWorld = hasWorld;
    }

    public EnumSet<TickType> ticks() {
        return EnumSet.of(TickType.CLIENT);
    }

    public String getLabel() {
        return "Multiplayer164-TerracottaLifecycle";
    }
}
