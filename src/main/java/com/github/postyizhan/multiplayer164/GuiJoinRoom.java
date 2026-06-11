package com.github.postyizhan.multiplayer164;

import com.github.postyizhan.multiplayer164.terracotta.TerracottaManager;
import com.github.postyizhan.multiplayer164.terracotta.TerracottaMetadata;
import com.github.postyizhan.multiplayer164.terracotta.TerracottaState;
import com.github.postyizhan.multiplayer164.util.I18n;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerAddress;

/**
 * Guest-side screen: enter an invitation code and join the host's room via Terracotta.
 * On success Terracotta starts a local virtual "lobby" server that appears in the LAN
 * server list, so the player returns to the multiplayer screen to double-click it.
 */
public class GuiJoinRoom extends GuiScreen {
    private static final int BUTTON_JOIN = 220;
    private static final int BUTTON_CANCEL = 221;

    private final GuiScreen parentScreen;
    private GuiTextField codeField;

    private volatile String status;
    private volatile String errorMessage;
    private volatile boolean joined;
    private volatile boolean joining;
    private volatile boolean connectRequested;
    private volatile String connectHost;
    private volatile int connectPort;

    public GuiJoinRoom(GuiScreen parent) {
        this.parentScreen = parent;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(BUTTON_JOIN, this.width / 2 - 100, this.height - 52,
                I18n.tr("multiplayer164.guest.join", "加入")));
        this.buttonList.add(new GuiButton(BUTTON_CANCEL, this.width / 2 - 100, this.height - 28,
                I18n.tr("gui.cancel", "取消")));

        this.codeField = new GuiTextField(this.fontRenderer, this.width / 2 - 100, 80, 200, 20);
        this.codeField.setFocused(true);
        this.codeField.setMaxStringLength(64);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_CANCEL) {
            if (joining && !joined) {
                joining = false;
                TerracottaManager.getInstance().reset();
            }
            this.mc.displayGuiScreen(parentScreen);
        } else if (button.id == BUTTON_JOIN) {
            attemptJoin();
        }
    }

    private void attemptJoin() {
        if (joining) {
            return;
        }
        final String code = this.codeField.getText().trim();
        if (code.isEmpty()) {
            this.errorMessage = I18n.tr("multiplayer164.guest.empty", "请输入邀请码。");
            return;
        }
        if (!TerracottaManager.getInstance().isPlatformSupported()) {
            this.errorMessage = I18n.tr("multiplayer164.error.platform", "当前平台暂不支持联机（仅支持 64 位 Windows）。");
            return;
        }
        this.joining = true;
        this.errorMessage = null;
        this.status = I18n.tr("multiplayer164.guest.connecting", "正在加入房间...");
        String playerName = resolvePlayerName();

        TerracottaManager.getInstance().join(code, playerName, new TerracottaManager.Callback() {
            public void onState(TerracottaState state) {
                if (state.kind == TerracottaState.Kind.GUEST_OK) {
                    joined = true;
                    joining = false;
                    String address = normalizeAddress(state.url);
                    ServerAddress serverAddress = ServerAddress.func_78860_a(address);
                    connectHost = serverAddress.getIP();
                    connectPort = serverAddress.getPort();
                    connectRequested = true;
                    status = I18n.trf("multiplayer164.guest.auto_connecting",
                            "已加入房间，正在自动连接本地大厅：%s", address);
                } else if (state.isException()) {
                    joining = false;
                    errorMessage = describeException(state.exceptionType);
                } else {
                    status = describeProgress(state.kind);
                }
            }

            public void onError(String message) {
                joining = false;
                errorMessage = GuiHostRoom.mapError(message);
            }
        });
    }

    @Override
    public void onGuiClosed() {
        if (joining && !joined) {
            joining = false;
            TerracottaManager.getInstance().reset();
        }
        super.onGuiClosed();
    }

    @Override
    protected void keyTyped(char ch, int key) {
        if (!joined && this.codeField.textboxKeyTyped(ch, key)) {
            return;
        }
        // Enter triggers join
        if (key == 28 || key == 156) {
            if (!joined) {
                attemptJoin();
            }
            return;
        }
        super.keyTyped(ch, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        this.codeField.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        if (connectRequested && connectHost != null && connectPort > 0) {
            connectRequested = false;
            this.mc.displayGuiScreen(new GuiConnecting(parentScreen, this.mc, connectHost, connectPort));
            return;
        }
        this.codeField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRenderer,
                I18n.tr("multiplayer164.guest.title", "加入联机房间"), this.width / 2, 30, 0xFFFFFF);
        this.drawString(this.fontRenderer,
                I18n.tr("multiplayer164.guest.prompt", "输入主机分享的邀请码："),
                this.width / 2 - 100, 65, 0xA0A0A0);
        this.codeField.drawTextBox();

        if (errorMessage != null) {
            this.drawCenteredString(this.fontRenderer, "§c" + errorMessage, this.width / 2, 120, 0xFFFFFF);
        } else if (status != null) {
            this.drawCenteredString(this.fontRenderer, (joined ? "§a" : "§e") + status, this.width / 2, 120, 0xFFFFFF);
        }

        this.drawCenteredString(this.fontRenderer,
                "§7" + I18n.tr("multiplayer164.region", "注意：联机服务目前仅对中国大陆地区可用。"),
                this.width / 2, this.height - 76, 0xFFFFFF);
        this.drawCenteredString(this.fontRenderer, "§8" + TerracottaMetadata.COPYRIGHT_NOTICE,
                this.width / 2, this.height - 64, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partial);
    }

    private String resolvePlayerName() {
        if (ConfigHandler.lastPlayerName != null && !ConfigHandler.lastPlayerName.trim().isEmpty()) {
            return ConfigHandler.lastPlayerName.trim();
        }
        if (this.mc.thePlayer != null) {
            return this.mc.thePlayer.username;
        }
        return this.mc.getSession() != null ? this.mc.getSession().getUsername() : "Player";
    }

    private static String normalizeAddress(String url) {
        String address = url == null ? "" : url.trim();
        if (address.startsWith("mc://")) {
            address = address.substring("mc://".length());
        } else {
            int scheme = address.indexOf("://");
            if (scheme >= 0) {
                address = address.substring(scheme + 3);
            }
        }
        int slash = address.indexOf('/');
        if (slash >= 0) {
            address = address.substring(0, slash);
        }
        return address.length() == 0 ? "127.0.0.1" : address;
    }

    private static String describeProgress(TerracottaState.Kind kind) {
        if (kind == TerracottaState.Kind.GUEST_CONNECTING) {
            return I18n.tr("multiplayer164.guest.connecting", "正在加入房间...");
        } else if (kind == TerracottaState.Kind.GUEST_STARTING) {
            return I18n.tr("multiplayer164.guest.starting", "正在建立连接...");
        }
        return I18n.tr("multiplayer164.guest.connecting", "正在加入房间...");
    }

    private static String describeException(String type) {
        if ("guest-et-crash".equals(type)) {
            return I18n.tr("multiplayer164.error.guest_crash", "加入失败：联机核心崩溃，请重试或反馈。");
        } else if ("ping-host-fail".equals(type) || "ping-host-rst".equals(type)) {
            return I18n.tr("multiplayer164.error.host_unreachable", "无法连接到主机，请确认邀请码正确且主机在线。");
        } else if ("scaffolding-invalid-response".equals(type)) {
            return I18n.tr("multiplayer164.error.invalid_response", "协议错误：主机返回了无效响应。");
        }
        return I18n.trf("multiplayer164.error.generic", "联机出错：%s", type == null ? "unknown" : type);
    }
}
