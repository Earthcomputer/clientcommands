package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

public class ServerBrandManager {

    private static boolean hasWarnedRng = false;

    public static void registerEvents() {
        ClientConnectionEvents.DISCONNECT.register(ServerBrandManager::onDisconnect);
    }

    public static String getServerBrand() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return "vanilla";
        }
        String brand = connection.serverBrand();
        return brand == null ? "vanilla" : brand;
    }

    public static boolean isVanilla() {
        return "vanilla".equals(getServerBrand());
    }

    private static void onDisconnect() {
        if (hasWarnedRng && Relogger.isRelogging) {
            Relogger.relogSuccessTasks.add(() -> hasWarnedRng = true);
        }
        hasWarnedRng = false;
    }

    public static void rngWarning() {
        if (!isVanilla() && !hasWarnedRng && !Minecraft.getInstance().hasSingleplayerServer()) {
            ClientCommandHelper.sendFeedback(
                    Component.translatable("playerManip.serverBrandWarning").withStyle(ChatFormatting.YELLOW));
            hasWarnedRng = true;
        }
    }

}
