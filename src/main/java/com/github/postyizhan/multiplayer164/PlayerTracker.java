package com.github.postyizhan.multiplayer164;

import cpw.mods.fml.common.IPlayerTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScorePlayerTeam;

public class PlayerTracker implements IPlayerTracker {

    @Override
    public void onPlayerLogin(EntityPlayer player) {
        /*
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && !server.isPVPEnabled()) {
            // PVP is disabled, add to NoPVP team if it exists
            Scoreboard scoreboard = server.worldServerForDimension(0).getScoreboard();
            ScorePlayerTeam team = scoreboard.getTeam("NoPVP");
            if (team != null) {
                scoreboard.addPlayerToTeam(player.username, team);
            }
        }
        */
    }

    @Override
    public void onPlayerLogout(EntityPlayer player) {
    }

    @Override
    public void onPlayerChangedDimension(EntityPlayer player) {
    }

    @Override
    public void onPlayerRespawn(EntityPlayer player) {
    }
}
