package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.CCNetworkHandler;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collection;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WhisperEncryptedCommand {

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cwe.playerNotFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwe")
                .then(argument("player", gameProfile())
                    .then(argument("message", greedyString())
                        .executes((ctx) -> whisper(ctx.getSource(), getCProfileArgument(ctx, "player"), getString(ctx, "message"))))));
    }

    private static int whisper(FabricClientCommandSource source, Collection<GameProfile> profiles, String message) throws CommandSyntaxException {
        assert source.getClient().getConnection() != null;
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        PlayerInfo recipient = source.getClient().getConnection().getOnlinePlayers().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(profiles.iterator().next().getName()))
                .findFirst()
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        MessageC2CPacket packet = new MessageC2CPacket(source.getClient().getConnection().getLocalGameProfile().getName(), message);
        CCNetworkHandler.getInstance().sendPacket(packet, recipient);
        MutableComponent prefix = Component.empty();
        prefix.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal("/cwe").withStyle(ChatFormatting.AQUA));
        prefix.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal(" "));
        Component component = prefix.append(Component.translatable("ccpacket.messageC2CPacket.outgoing", recipient.getProfile().getName(), message).withStyle(ChatFormatting.GRAY));
        source.sendFeedback(component);
        return Command.SINGLE_SUCCESS;
    }
}
