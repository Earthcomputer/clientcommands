package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.xpple.clientarguments.arguments.CEnumArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CGameModeCommand {

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cgamemode.playerNotFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cgamemode")
            .then(literal("player")
                .then(argument("player", gameProfile())
                    .executes(ctx -> getPlayerGameMode(ctx.getSource(), getCProfileArgument(ctx, "player")))))
            .then(literal("list")
                .then(argument("gameMode", enumArg(GameType.class))
                    .executes(ctx -> listWithGameMode(ctx.getSource(), getEnum(ctx, "gameMode"))))));
    }

    private static int getPlayerGameMode(FabricClientCommandSource source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        PlayerInfo player = source.getClient().getConnection().getOnlinePlayers().stream()
            .filter(p -> p.getProfile().getName().equalsIgnoreCase(profiles.iterator().next().getName()))
            .findAny()
            .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        source.sendFeedback(Component.translatable("commands.cgamemode.playerGameMode", player.getProfile().getName(), player.getGameMode().getShortDisplayName()));
        return Command.SINGLE_SUCCESS;
    }

    private static int listWithGameMode(FabricClientCommandSource source, GameType gameMode) {
        Set<PlayerInfo> playersWithGameMode = source.getClient().getConnection().getOnlinePlayers().stream()
            .filter(p -> p.getGameMode() == gameMode)
            .collect(Collectors.toSet());

        if (playersWithGameMode.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cgamemode.noneWithGameMode", gameMode.getShortDisplayName()));
        } else {
            source.sendFeedback(Component.translatable("commands.cgamemode.listWithGamemode", gameMode.getShortDisplayName()));
            playersWithGameMode.forEach(p -> source.sendFeedback(Component.literal(p.getProfile().getName())));
        }

        return playersWithGameMode.size();
    }
}
