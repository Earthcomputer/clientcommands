package net.earthcomputer.clientcommands.features;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;

import java.util.ArrayList;
import java.util.List;

public class Relogger {
    public static boolean isRelogging;
    public static final List<Runnable> relogSuccessTasks = new ArrayList<>();

    public static boolean disconnect() {
        return disconnect(false);
    }

    private static boolean disconnect(boolean relogging) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            return false;
        }

        boolean singleplayer = mc.isInSingleplayer();
        mc.world.disconnect();
        if (relogging) {
            isRelogging = true;
        }
        if (singleplayer) {
            mc.disconnect(new MessageScreen(Text.translatable("menu.savingLevel")));
        } else {
            mc.disconnect();
        }
        isRelogging = false;

        if (singleplayer) {
            mc.setScreen(new TitleScreen());
        } else {
            mc.setScreen(new MultiplayerScreen(new TitleScreen()));
        }

        return true;
    }

    public static boolean relog() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isInSingleplayer()) {
            IntegratedServer server = MinecraftClient.getInstance().getServer();
            if (server == null) {
                return false;
            }
            String levelName = server.getSavePath(WorldSavePath.ROOT).normalize().getFileName().toString();
            if (!disconnect(true)) {
                return false;
            }
            if (!mc.getLevelStorage().levelExists(levelName)) {
                return false;
            }
            mc.setScreenAndRender(new MessageScreen(Text.translatable("selectWorld.data_read")));
            mc.createIntegratedServerLoader().start(mc.currentScreen, levelName);
            return true;
        } else {
            ServerInfo serverInfo = mc.getCurrentServerEntry();
            if (serverInfo == null) {
                return false;
            }
            if (!disconnect(true)) {
                return false;
            }
            ConnectScreen.connect(mc.currentScreen, mc, ServerAddress.parse(serverInfo.address), serverInfo);
            return true;
        }
    }

    public static void cantHaveRelogSuccess() {
        relogSuccessTasks.clear();
    }

    public static void onRelogSuccess() {
        for (Runnable task : relogSuccessTasks) {
            task.run();
        }
        relogSuccessTasks.clear();
    }
}
