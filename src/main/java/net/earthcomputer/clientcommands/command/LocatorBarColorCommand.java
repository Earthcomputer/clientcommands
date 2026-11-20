package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

import java.util.UUID;

import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class LocatorBarColorCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("clocatorbarcolor")
            .executes(ctx -> getLocatorBarColor(ctx.getSource()))
            .then(argument("player", gameProfile(true))
                .executes(ctx -> getLocatorBarColor(ctx.getSource(), getSingleProfileArgument(ctx, "player")))));
    }

    private static int getLocatorBarColor(FabricClientCommandSource source) {
        return getLocatorBarColor(source, source.getPlayer().getGameProfile());
    }

    private static int getLocatorBarColor(FabricClientCommandSource source, GameProfile gameProfile) {
        UUID uuid = gameProfile.id();
        int internalColor = ARGB.color(255, uuid.hashCode());
        int inGameColor = ARGB.setBrightness(internalColor, 0.9F);

        Component inGameColorComponent = Component.literal(String.format("%06x", ARGB.transparent(inGameColor)))
            .withStyle(style -> style.withColor(inGameColor));
        Component internalColorComponent = Component.literal(String.format("%06x", ARGB.transparent(internalColor)))
            .withStyle(style -> style.withColor(internalColor));

        source.sendFeedback(Component.translatable("commands.clocatorbarcolor.success", gameProfile.name(), inGameColorComponent, internalColorComponent));

        return internalColor;
    }
}
