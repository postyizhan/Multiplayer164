package com.github.postyizhan.multiplayer164;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.event.ForgeSubscribe;

public class LanGuiHandler {
    @ForgeSubscribe
    public void onGuiOpen(GuiOpenEvent event) {
        if (event.gui instanceof GuiShareToLan && !(event.gui instanceof GuiCustomShareToLan)) {
            // Pass the current screen as parent (usually GuiIngameMenu)
            event.gui = new GuiCustomShareToLan(Minecraft.getMinecraft().currentScreen);
        }
    }
}
