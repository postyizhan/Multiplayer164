package com.github.postyizhan.multiplayer164;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.EnumGameType;
import net.minecraft.util.HttpUtil;

public class LanServerController {

    public static String startLan(Minecraft mc, int port, String gameMode, boolean allowCheats, boolean pvpEnabled) {
        try {
            IntegratedServer server = mc.getIntegratedServer();
            if (server == null) return null;

            // 1. Set Game Type and Cheats
            EnumGameType type = EnumGameType.getByName(gameMode);
            server.getConfigurationManager().setGameType(type);
            server.getConfigurationManager().setCommandsAllowedForAll(allowCheats);

            // 2. Set PVP
            try {
                server.setAllowPvp(pvpEnabled);
            } catch (NoSuchMethodError e) {
                setField(MinecraftServer.class, server, pvpEnabled, "allowPvP", "field_71318_t");
            }
            
            // 3. Bind Port (The core 1.6.4 logic)
            // Get IntegratedServerListenThread
            Object networkThread = server.getNetworkThread(); // Returns NetworkListenThread
            if (!networkThread.getClass().getName().equals("net.minecraft.server.integrated.IntegratedServerListenThread")) {
                System.err.println("NetworkThread is not IntegratedServerListenThread: " + networkThread.getClass().getName());
                return null;
            }

            // Access field_71757_g (myServerListenThread) in IntegratedServerListenThread
            Field fieldListenThread = getField(networkThread.getClass(), "myServerListenThread", "field_71757_g");
            if (fieldListenThread == null) {
                 System.err.println("Could not find field_71757_g");
                 return null;
            }
            
            Object existingListenThread = fieldListenThread.get(networkThread);
            if (existingListenThread == null) {
                // Create new ServerListenThread
                Class<?> clsServerListenThread = Class.forName("net.minecraft.server.ServerListenThread");
                Class<?> clsNetworkListenThread = Class.forName("net.minecraft.network.NetworkListenThread");
                
                // Debug constructors
                // ... (debug code removed for brevity) ...

                // Try to find the correct constructor - in 1.6.4 it takes NetworkListenThread, InetAddress, int
                Constructor<?> ctor = null;
                try {
                    ctor = clsServerListenThread.getConstructor(clsNetworkListenThread, InetAddress.class, int.class);
                } catch (NoSuchMethodException e) {
                    // System.out.println("Standard constructor not found, searching...");
                }

                if (ctor == null) {
                     // Fallback: search for any constructor with 3 args: (NetworkListenThread, InetAddress, int)
                     for (Constructor<?> c : clsServerListenThread.getConstructors()) {
                         Class<?>[] types = c.getParameterTypes();
                         if (types.length == 3 && 
                             clsNetworkListenThread.isAssignableFrom(types[0]) && 
                             types[1] == InetAddress.class && 
                             types[2] == int.class) {
                             ctor = c;
                             break;
                         }
                     }
                }

                if (ctor != null) {
                    // Instantiate with networkThread (which is an instance of NetworkListenThread)
                    Object newListenThread = ctor.newInstance(networkThread, null, port);
                    
                    // Set it
                    fieldListenThread.set(networkThread, newListenThread);
                    
                    // Start the thread (ServerListenThread extends Thread)
                    if (newListenThread instanceof Thread) {
                        ((Thread)newListenThread).start();
                    }
                } else {
                    System.err.println("Could not find suitable constructor for ServerListenThread");
                    return null;
                }
            } else {
                System.out.println("ServerListenThread already exists.");
            }

            // 4. Start LAN Ping (ThreadLanServerPing)
            // IntegratedServer.lanServerPing (field_71345_q)
            Field fieldLanPing = getField(IntegratedServer.class, "lanServerPing", "field_71345_q");
            Object existingPing = fieldLanPing.get(server);
            if (existingPing == null) {
                Class<?> clsLanPing = Class.forName("net.minecraft.client.multiplayer.ThreadLanServerPing");
                Constructor<?> ctorPing = clsLanPing.getConstructor(String.class, String.class);
                Object newPing = ctorPing.newInstance(server.getMOTD(), String.valueOf(port));
                
                fieldLanPing.set(server, newPing);
                
                if (newPing instanceof Thread) {
                    ((Thread)newPing).start();
                }
            }

            // 5. Set isPublic = true
            setField(IntegratedServer.class, server, true, "isPublic", "field_71346_p"); // Corrected field name
            
            System.out.println("LAN World started on port " + port);
            return String.valueOf(port);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void setField(Class<?> clazz, Object instance, Object value, String... names) {
        Field f = getField(clazz, names);
        if (f != null) {
            try {
                f.set(instance, value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private static Field getField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // continue
            }
        }
        if (clazz.getSuperclass() != null) {
            return getField(clazz.getSuperclass(), names);
        }
        return null;
    }
}
