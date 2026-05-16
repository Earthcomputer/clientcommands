package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.event.MoreScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Relogger {
    public static boolean isRelogging;
    @Nullable
    public static ServerData cachedServerData;
    public static final List<Runnable> relogSuccessTasks = new ArrayList<>();

    static {
        MoreScreenEvents.BEFORE_ADD.register(Relogger::onAddScreen);
    }

    private static boolean disconnect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }

        boolean singleplayer = mc.isLocalServer();
        mc.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
        isRelogging = true;
        if (singleplayer) {
            mc.disconnectWithSavingScreen();
        } else {
            mc.disconnectWithProgressScreen();
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
            IntegratedServer server = mc.getSingleplayerServer();
            if (!disconnect()) {
                return false;
            }
            return relogToIntegratedServer(server);
        } else {
            ServerData server = mc.getCurrentServer();
            if (!disconnect()) {
                return false;
            }
            return relogToDedicatedServer(server);
        }
    }

    private static boolean relogToIntegratedServer(@Nullable IntegratedServer server) {
        Minecraft mc = Minecraft.getInstance();

        if (server == null) {
            return false;
        }
        String levelName = server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
        if (!mc.getLevelSource().levelExists(levelName)) {
            return false;
        }
        mc.createWorldOpenFlows().openWorld(levelName, () -> mc.setScreen(new TitleScreen()));
        return true;
    }

    private static boolean relogToDedicatedServer(@Nullable ServerData serverData) {
        Minecraft mc = Minecraft.getInstance();

        if (serverData == null) {
            return false;
        }
        isRelogging = true;
        cachedServerData = serverData;
        mc.setScreen(new TitleScreen());
        ConnectScreen.startConnecting(mc.screen, mc, ServerAddress.parseString(serverData.ip), serverData, false, null);
        return true;
    }

    public static void onFailedRelog() {
        if (cachedServerData == null) {
            return;
        }
        isRelogging = false;
        ServerData serverData = cachedServerData;
        cachedServerData = null;
        relogToDedicatedServer(serverData);
    }

    private static boolean onAddScreen(@Nullable Screen screen) {
        if (screen instanceof ConnectScreen && isRelogging) {
            return false;
        }

        if (screen != null
            && !(screen instanceof GenericMessageScreen)
            && !(screen instanceof LevelLoadingScreen)
            && !(screen instanceof ProgressScreen)
            && !(screen instanceof ConnectScreen)
            && !(screen instanceof PauseScreen)
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
        isRelogging = false;
        cachedServerData = null;
        return result;
    }

    public static boolean isRateLimitMessage(Component error) {
        return error.getContents() instanceof TranslatableContents translate && translate.getKey().equals("disconnect.loginFailedInfo") && translate.getArgs()[0].toString().equals("RateLimiter disallowed request");
    }
}
