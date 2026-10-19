package net.earthcomputer.clientcommands;

import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.network.chat.Component;

public final class Donate {
    private Donate() {
    }

    public static void registerEvents() {
        ClientPlayConnectionEvents.JOIN.register((_, _, _) -> run());
    }

    private static void run() {
        if (Configs.donationMessage) {
            ClientCommandHelper.sendHelp(
                Component.translatable(
                    "commands.client.donate",
                    CComponentUtil.getLinkTextComponent("commands.client.donate.link", "https://ko-fi.com/earthcomputer")
                )
            );

            Configs.donationMessage = false;
            Configs.save();
        }
    }
}
