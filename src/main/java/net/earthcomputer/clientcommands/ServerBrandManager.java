package net.earthcomputer.clientcommands;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

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
        hasWarnedRng = false;
    }

    public static void rngWarning() {
        if (!isVanilla() && !hasWarnedRng && !MinecraftClient.getInstance().isIntegratedServerRunning()) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                    new TranslatableText("playerManip.serverBrandWarning").formatted(Formatting.YELLOW));
            hasWarnedRng = true;
        }
    }

}
