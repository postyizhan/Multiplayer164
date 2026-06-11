package com.github.postyizhan.multiplayer164;

import com.github.postyizhan.multiplayer164.util.I18n;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;

/**
 * Drop-in replacement for the vanilla multiplayer server list that adds a
 * "Join multiplayer room" button. Installed by {@link MultiplayerGuiHandler} via the
 * {@code GuiOpenEvent}. Clicking the button opens {@link GuiJoinRoom} to enter an
 * invitation code; once joined, Terracotta's virtual lobby appears in the LAN list.
 */
public class GuiCustomMultiplayer extends GuiMultiplayer {
    /** High IDs to avoid colliding with vanilla GuiMultiplayer button IDs. */
    private static final int BUTTON_JOIN_ROOM = 210;

    private final GuiScreen grandParent;

    public GuiCustomMultiplayer(GuiScreen parent) {
        super(parent);
        this.grandParent = parent;
    }

    @Override
    public void initGui() {
        super.initGui();
        // Add our button below the standard control row. Placed at bottom-left.
        this.buttonList.add(new GuiButton(BUTTON_JOIN_ROOM, this.width / 2 - 154, this.height - 28, 100, 20,
                I18n.tr("multiplayer164.guest.join_button", "加入联机房间")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_JOIN_ROOM) {
            this.mc.displayGuiScreen(new GuiJoinRoom(this));
            return;
        }
        super.actionPerformed(button);
    }
}
