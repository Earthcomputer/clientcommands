package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

import java.util.Collection;

import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class PingCommand {

    private static final SimpleCommandExceptionType PLAYER_IN_SINGLEPLAYER_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cping.singleplayer"));
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cping.playerNotFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cping")
            .executes(ctx -> printPing(ctx.getSource()))
            .then(argument("player", gameProfile())
                .executes(ctx -> printPing(ctx.getSource(), getCProfileArgument(ctx, "player"))))
        );
    }

    private static int printPing(FabricClientCommandSource source) throws CommandSyntaxException {
        if (source.getClient().isInSingleplayer()) {
            throw PLAYER_IN_SINGLEPLAYER_EXCEPTION.create();
        }

        int ping = getLocalPing();
        if (ping == -1) {
            throw PLAYER_IN_SINGLEPLAYER_EXCEPTION.create();
        }

        source.sendFeedback(Text.translatable("commands.cping.success", ping));
        return Command.SINGLE_SUCCESS;
    }

    private static int printPing(FabricClientCommandSource source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (source.getClient().isInSingleplayer()) {
            throw PLAYER_IN_SINGLEPLAYER_EXCEPTION.create();
        }

        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        GameProfile profile = profiles.iterator().next();

        ClientPlayNetworkHandler networkHandler = source.getClient().getNetworkHandler();
        assert networkHandler != null;
        PlayerListEntry player = networkHandler.getPlayerListEntry(profile.getId());
        if (player == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        int ping = player.getLatency();
        source.sendFeedback(Text.translatable("commands.cping.success.other", profile.getName(), ping));
        return Command.SINGLE_SUCCESS;
    }

    public static int getLocalPing() {
        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
        if (networkHandler == null) {
            return -1;
        }

        PlayerListEntry localPlayer = networkHandler.getPlayerListEntry(networkHandler.getProfile().getId());
        if (localPlayer == null) {
            return -1;
        }

        return localPlayer.getLatency();
    }
}
