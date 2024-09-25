package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class PingCommand {

    private static final SimpleCommandExceptionType PLAYER_IN_SINGLEPLAYER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cping.singleplayer"));
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cping.playerNotFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cping")
            .executes(ctx -> printPing(ctx.getSource()))
            .then(argument("player", gameProfile(true))
                .executes(ctx -> printPing(ctx.getSource(), getSingleProfileArgument(ctx, "player"))))
        );
    }

    private static int printPing(FabricClientCommandSource source) throws CommandSyntaxException {
        if (source.getClient().isLocalServer()) {
            throw PLAYER_IN_SINGLEPLAYER_EXCEPTION.create();
        }

        int ping = getLocalPing();
        if (ping == -1) {
            throw PLAYER_IN_SINGLEPLAYER_EXCEPTION.create();
        }

        source.sendFeedback(Component.translatable("commands.cping.success", ping));
        return Command.SINGLE_SUCCESS;
    }

    private static int printPing(FabricClientCommandSource source, GameProfile player) throws CommandSyntaxException {
        if (source.getClient().isLocalServer()) {
            throw PLAYER_IN_SINGLEPLAYER_EXCEPTION.create();
        }

        PlayerInfo playerInfo = source.getClient().getConnection().getPlayerInfo(player.getId());
        if (playerInfo == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        int ping = playerInfo.getLatency();
        source.sendFeedback(Component.translatable("commands.cping.success.other", playerInfo.getProfile().getName(), ping));
        return Command.SINGLE_SUCCESS;
    }

    public static int getLocalPing() {
        ClientPacketListener packetListener = Minecraft.getInstance().getConnection();
        if (packetListener == null) {
            return -1;
        }

        PlayerInfo localPlayer = packetListener.getPlayerInfo(packetListener.getLocalGameProfile().getId());
        if (localPlayer == null) {
            return -1;
        }

        return localPlayer.getLatency();
    }
}
