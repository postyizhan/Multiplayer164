package com.github.postyizhan.multiplayer164;

import com.github.postyizhan.multiplayer164.terracotta.TerracottaManager;
import com.github.postyizhan.multiplayer164.terracotta.TerracottaMetadata;
import com.github.postyizhan.multiplayer164.terracotta.TerracottaState;
import com.github.postyizhan.multiplayer164.util.I18n;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * Host-side multiplayer screen. Shown after the player opens a world to LAN. Drives
 * {@link TerracottaManager#host} and, once Terracotta reports {@code HOST_OK}, displays
 * the invitation code (auto-copied to the clipboard) for friends to join with.
 */
public class GuiHostRoom extends GuiScreen {
    private final GuiScreen parentScreen;
    private final String playerName;

    private volatile String status;
    private volatile String inviteCode;
    private volatile String pendingChatInviteCode;
    private volatile String errorMessage;
    private boolean hostingStarted;

    public GuiHostRoom(GuiScreen parent, String playerName) {
        this.parentScreen = parent;
        this.playerName = playerName;
        this.status = I18n.tr("multiplayer164.host.creating", "正在创建房间...");
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        // Copy code button (ID 201), only useful once we have a code
        this.buttonList.add(new GuiButton(201, this.width / 2 - 100, this.height - 52,
                I18n.tr("multiplayer164.host.copy", "复制邀请码")));
        // Done/back button (ID 202)
        this.buttonList.add(new GuiButton(202, this.width / 2 - 100, this.height - 28,
                I18n.tr("gui.done", "完成")));
        if (!hostingStarted) {
            hostingStarted = true;
            startHosting();
        }
    }

    private void startHosting() {
        if (!TerracottaManager.getInstance().isPlatformSupported()) {
            this.errorMessage = I18n.tr("multiplayer164.error.platform", "当前平台暂不支持联机（仅支持 64 位 Windows）。");
            return;
        }
        TerracottaManager.getInstance().host(playerName, new TerracottaManager.Callback() {
            public void onState(TerracottaState state) {
                if (state.kind == TerracottaState.Kind.HOST_OK && state.room != null) {
                    if (!state.room.equals(inviteCode)) {
                        pendingChatInviteCode = state.room;
                    }
                    inviteCode = state.room;
                    status = null;
                    GuiScreen.setClipboardString(state.room);
                } else if (state.isException()) {
                    errorMessage = describeException(state.exceptionType);
                } else {
                    status = describeProgress(state.kind);
                }
            }

            public void onError(String message) {
                errorMessage = mapError(message);
            }
        });
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 201) {
            if (inviteCode != null) {
                GuiScreen.setClipboardString(inviteCode);
            }
        } else if (button.id == 202) {
            this.mc.displayGuiScreen(parentScreen);
        }
    }

    @Override
    public void updateScreen() {
        String code = pendingChatInviteCode;
        if (code != null) {
            pendingChatInviteCode = null;
            printInviteToChat(code);
        }
        super.updateScreen();
    }

    private void printInviteToChat(String code) {
        if (this.mc != null && this.mc.ingameGUI != null) {
            String copyUrl = GuiCopyChat.copyUrl(code);
            this.mc.ingameGUI.getChatGUI().printChatMessage(I18n.trf(
                    "multiplayer164.host.chat_invite",
                    "§a联机房间已创建，邀请码：§e%s §a点击复制：§b§n%s", code, copyUrl));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer,
                I18n.tr("multiplayer164.host.title", "联机房间"), this.width / 2, 30, 0xFFFFFF);

        if (errorMessage != null) {
            this.drawCenteredString(this.fontRenderer, "§c" + errorMessage, this.width / 2, 70, 0xFFFFFF);
        } else if (inviteCode != null) {
            this.drawCenteredString(this.fontRenderer,
                    I18n.tr("multiplayer164.host.created", "房间已创建！邀请码已复制到剪贴板："),
                    this.width / 2, 70, 0xFFFFFF);
            this.drawCenteredString(this.fontRenderer, "§a§l" + inviteCode, this.width / 2, 92, 0xFFFFFF);
            this.drawCenteredString(this.fontRenderer,
                    I18n.tr("multiplayer164.host.hint", "让朋友在「多人游戏」中点击「加入联机房间」并输入此邀请码。"),
                    this.width / 2, 118, 0xA0A0A0);
        } else {
            this.drawCenteredString(this.fontRenderer, status, this.width / 2, 80, 0xFFFFFF);
        }

        // Region + third-party copyright notice (required by Terracotta's AGPL exception).
        this.drawCenteredString(this.fontRenderer,
                "§7" + I18n.tr("multiplayer164.region", "注意：联机服务目前仅对中国大陆地区可用。"),
                this.width / 2, this.height - 76, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "§8" + TerracottaMetadata.COPYRIGHT_NOTICE,
                this.width / 2, this.height - 64, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partial);
    }

    static String describeProgress(TerracottaState.Kind kind) {
        if (kind == TerracottaState.Kind.HOST_SCANNING) {
            return I18n.tr("multiplayer164.host.scanning", "正在扫描局域网世界...");
        } else if (kind == TerracottaState.Kind.HOST_STARTING) {
            return I18n.tr("multiplayer164.host.starting", "正在建立房间...");
        }
        return I18n.tr("multiplayer164.host.creating", "正在创建房间...");
    }

    static String describeException(String type) {
        if ("host-et-crash".equals(type)) {
            return I18n.tr("multiplayer164.error.host_crash", "创建房间失败：联机核心崩溃，请重试或反馈。");
        } else if ("ping-server-rst".equals(type)) {
            return I18n.tr("multiplayer164.error.server_rst", "房间已关闭：你已退出该世界。");
        }
        return I18n.trf("multiplayer164.error.generic", "联机出错：%s", type == null ? "unknown" : type);
    }

    static String mapError(String message) {
        if ("platform-unsupported".equals(message)) {
            return I18n.tr("multiplayer164.error.platform", "当前平台暂不支持联机（仅支持 64 位 Windows）。");
        }
        if ("connect-failed".equals(message)) {
            return I18n.tr("multiplayer164.error.connect", "无法连接到联机核心，请重试。");
        }
        if (message != null && message.startsWith("http-")) {
            return I18n.trf("multiplayer164.error.http",
                    "联机核心返回错误（%s），请重试或重启游戏。", message.substring("http-".length()));
        }
        return I18n.trf("multiplayer164.error.generic", "联机出错：%s", message);
    }
}
