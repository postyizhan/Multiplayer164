package com.github.postyizhan.multiplayer164;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiShareToLan;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.EnumGameType;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatMessageComponent;

public class GuiCustomShareToLan extends GuiShareToLan {
    private final GuiScreen parentScreen;
    private GuiTextField portField;
    private GuiButton pvpButton;
    private GuiButton gameModeButton;
    private GuiButton allowCommandsButton;
    
    private String gameMode = "survival";
    private boolean allowCommands = false;
    private boolean pvpEnabled = true;

    public GuiCustomShareToLan(GuiScreen parent) {
        super(parent);
        this.parentScreen = parent;
        
        // Load default values from config
        this.gameMode = ConfigHandler.lastGameMode;
        this.allowCommands = ConfigHandler.lastAllowCommands;
        this.pvpEnabled = ConfigHandler.lastPvp;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        
        // Game Mode Button (ID 101)
        this.buttonList.add(this.gameModeButton = new GuiButton(101, this.width / 2 - 155, 100, 150, 20, StatCollector.translateToLocal("selectWorld.gameMode")));
        this.updateGameModeButton();

        // Allow Commands Button (ID 102)
        this.buttonList.add(this.allowCommandsButton = new GuiButton(102, this.width / 2 + 5, 100, 150, 20, StatCollector.translateToLocal("selectWorld.allowCommands")));
        this.updateAllowCommandsButton();

        // Port Field
        this.portField = new GuiTextField(this.fontRenderer, this.width / 2 - 155, 150, 150, 20);
        this.portField.setText(String.valueOf(ConfigHandler.lastPort)); // Load last port

        // PVP Button (ID 103)
        this.buttonList.add(this.pvpButton = new GuiButton(103, this.width / 2 + 5, 150, 150, 20, "PVP: " + (this.pvpEnabled ? "ON" : "OFF")));

        // Start LAN World Button (ID 104)
        this.buttonList.add(new GuiButton(104, this.width / 2 - 155, this.height - 28, 150, 20, StatCollector.translateToLocal("lanServer.start")));

        // Cancel Button (ID 105)
        this.buttonList.add(new GuiButton(105, this.width / 2 + 5, this.height - 28, 150, 20, StatCollector.translateToLocal("gui.cancel")));
    }

    private void updateGameModeButton() {
        this.gameModeButton.displayString = StatCollector.translateToLocal("selectWorld.gameMode") + ": " + StatCollector.translateToLocal("selectWorld.gameMode." + this.gameMode);
    }

    private void updateAllowCommandsButton() {
        this.allowCommandsButton.displayString = StatCollector.translateToLocal("selectWorld.allowCommands") + " " + (this.allowCommands ? StatCollector.translateToLocal("options.on") : StatCollector.translateToLocal("options.off"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 105) { // Cancel
            this.mc.displayGuiScreen(this.parentScreen);
        } else if (button.id == 104) { // Start LAN World
            String portStr = this.portField.getText();
            int port = 25565;
            try {
                port = Integer.parseInt(portStr);
                if (port < 1024 || port > 65535) port = 25565;
            } catch (NumberFormatException e) {
                port = 25565;
            }

            // Save Settings
            ConfigHandler.saveSettings(port, this.pvpEnabled, this.gameMode, this.allowCommands);

            // Start LAN
            String result = LanServerController.startLan(this.mc, port, this.gameMode, this.allowCommands, this.pvpEnabled);
            
            if (result != null) {
                this.mc.ingameGUI.getChatGUI().printChatMessage(StatCollector.translateToLocalFormatted("commands.publish.started", result));
            } else {
                this.mc.ingameGUI.getChatGUI().printChatMessage(StatCollector.translateToLocal("commands.publish.failed"));
            }
            this.mc.displayGuiScreen(null);

        } else if (button.id == 101) { // Game Mode
            if (this.gameMode.equals("survival")) {
                this.gameMode = "creative";
            } else if (this.gameMode.equals("creative")) {
                this.gameMode = "adventure";
            } else {
                this.gameMode = "survival";
            }
            this.updateGameModeButton();
        } else if (button.id == 102) { // Allow Commands
            this.allowCommands = !this.allowCommands;
            this.updateAllowCommandsButton();
        } else if (button.id == 103) { // PVP
            this.pvpEnabled = !this.pvpEnabled;
            this.pvpButton.displayString = "PVP: " + (this.pvpEnabled ? "ON" : "OFF");
        }
    }

    @Override
    public void drawScreen(int par1, int par2, float par3) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer, StatCollector.translateToLocal("lanServer.title"), this.width / 2, 50, 16777215);
        this.drawCenteredString(this.fontRenderer, StatCollector.translateToLocal("lanServer.otherPlayers"), this.width / 2, 82, 16777215);
        
        this.drawString(this.fontRenderer, "Port:", this.width / 2 - 155, 138, 10526880);
        
        this.portField.drawTextBox();
        super.drawScreen(par1, par2, par3);
    }

    @Override
    public void updateScreen() {
        this.portField.updateCursorCounter();
        super.updateScreen();
    }

    @Override
    protected void keyTyped(char par1, int par2) {
        if (this.portField.textboxKeyTyped(par1, par2)) {
            return;
        }
        super.keyTyped(par1, par2);
    }

    @Override
    protected void mouseClicked(int par1, int par2, int par3) {
        super.mouseClicked(par1, par2, par3);
        this.portField.mouseClicked(par1, par2, par3);
    }
}
