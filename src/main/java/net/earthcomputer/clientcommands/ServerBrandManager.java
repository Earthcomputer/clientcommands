package net.earthcomputer.clientcommands;

import net.earthcomputer.clientcommands.features.Relogger;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ServerBrandManager {

    private static String serverBrand = "vanilla";
    private static boolean hasWarnedRng = false;

    public static void setServerBrand(String brand) {
        serverBrand = brand;
    }

    public static String getServerBrand() {
        return serverBrand;
    }

    public static boolean isVanilla() {
        return "vanilla".equals(serverBrand);
    }

    public static void onDisconnect() {
        if (hasWarnedRng && Relogger.isRelogging) {
            Relogger.relogSuccessTasks.add(() -> hasWarnedRng = true);
        }
        hasWarnedRng = false;
    }

    public static void rngWarning() {
        if (!isVanilla() && !hasWarnedRng && !Minecraft.getInstance().hasSingleplayerServer()) {
            Minecraft.getInstance().gui.getChat().addMessage(
                    Component.translatable("playerManip.serverBrandWarning").withStyle(ChatFormatting.YELLOW));
            hasWarnedRng = true;
        }
    }

}
