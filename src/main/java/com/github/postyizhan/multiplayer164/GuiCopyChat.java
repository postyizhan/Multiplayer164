package com.github.postyizhan.multiplayer164;

import com.github.postyizhan.multiplayer164.util.I18n;

import net.minecraft.client.gui.ChatClickData;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * Forge/Minecraft 1.6.4 chat only supports clickable URL detection, not modern
 * click events like COPY_TO_CLIPBOARD. This chat screen keeps vanilla behavior, but
 * intercepts Multiplayer164's special copy URL and copies the embedded invite code
 * instead of opening a browser.
 */
public class GuiCopyChat extends GuiChat {
    private static final String COPY_HOST = "m164.copy";

    public GuiCopyChat() {
        super();
    }

    public GuiCopyChat(String defaultText) {
        super(defaultText == null ? "" : defaultText);
    }

    public static String copyUrl(String inviteCode) {
        return "https://" + COPY_HOST + "/" + encode(inviteCode);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (button == 0 && this.mc != null && this.mc.ingameGUI != null) {
            ChatClickData data = this.mc.ingameGUI.getChatGUI().func_73766_a(Mouse.getX(), Mouse.getY());
            String inviteCode = data == null ? null : parseCopyUrl(data.getClickedUrl());
            if (inviteCode != null) {
                GuiScreen.setClipboardString(inviteCode);
                this.mc.ingameGUI.getChatGUI().printChatMessage(I18n.trf(
                        "multiplayer164.host.chat_copied",
                        "§a已复制联机邀请码到剪贴板：§e%s", inviteCode));
                return;
            }
        }
        super.mouseClicked(x, y, button);
    }

    private static String parseCopyUrl(String clickedUrl) {
        if (clickedUrl == null) {
            return null;
        }
        try {
            URI uri = new URI(clickedUrl);
            if (!COPY_HOST.equalsIgnoreCase(uri.getHost())) {
                return null;
            }
            String path = uri.getRawPath();
            if (path == null || path.length() <= 1) {
                return null;
            }
            String code = URLDecoder.decode(path.substring(1), "UTF-8");
            return code.length() == 0 ? null : code;
        } catch (Exception e) {
            return null;
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value == null ? "" : value;
        }
    }
}
