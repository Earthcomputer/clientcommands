package net.earthcomputer.clientcommands;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

public class ServerBrandManager {

    private static String serverBrand = "vanilla";

    public static void setServerBrand(String brand) {
        serverBrand = brand;
    }

    public static String getServerBrand() {
        return serverBrand;
    }

    public static boolean isVanilla() {
        return "vanilla".equals(serverBrand);
    }

    public static void rngWarning() {
        if (!isVanilla()) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                    new TranslatableText("enchCrack.serverBrandWarning").formatted(Formatting.YELLOW));
        }
    }

}
