package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.event.MoreScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Relogger {
    public static boolean isRelogging;
    public static final List<Runnable> relogSuccessTasks = new ArrayList<>();

    static {
        MoreScreenEvents.BEFORE_ADD.register(Relogger::onAddScreen);
    }

    public static boolean disconnect() {
        return disconnect(false);
    }

    private static boolean disconnect(boolean relogging) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        boolean singleplayer = mc.isLocalServer();
        mc.level.disconnect();
        if (relogging) {
            isRelogging = true;
        }
        if (singleplayer) {
            mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            mc.disconnect();
        }
        isRelogging = false;

        if (singleplayer) {
            mc.setScreen(new TitleScreen());
        } else {
            mc.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
        }

        return true;
    }

    public static boolean relog() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isLocalServer()) {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) {
                return false;
            }
            String levelName = server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
            if (!disconnect(true)) {
                return false;
            }
            if (!mc.getLevelSource().levelExists(levelName)) {
                return false;
            }
            mc.createWorldOpenFlows().openWorld(levelName, () -> mc.setScreen(new TitleScreen()));
            return true;
        } else {
            ServerData serverData = mc.getCurrentServer();
            if (serverData == null) {
                return false;
            }
            if (!disconnect(true)) {
                return false;
            }
            ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(serverData.ip), serverData, false, null);
            return true;
        }
    }

    private static boolean onAddScreen(@Nullable Screen screen) {
        if (screen != null
            && !(screen instanceof GenericMessageScreen)
            && !(screen instanceof LevelLoadingScreen)
            && !(screen instanceof ProgressScreen)
            && !(screen instanceof ConnectScreen)
            && !(screen instanceof PauseScreen)
            && !(screen instanceof ReceivingLevelScreen)
            && !(screen instanceof TitleScreen)
            && !(screen instanceof JoinMultiplayerScreen)
        ) {
            relogSuccessTasks.clear();
        }

        return true;
    }

    public static boolean onRelogSuccess() {
        boolean result = !relogSuccessTasks.isEmpty();
        for (Runnable task : relogSuccessTasks) {
            task.run();
        }
        relogSuccessTasks.clear();
        return result;
    }
}
