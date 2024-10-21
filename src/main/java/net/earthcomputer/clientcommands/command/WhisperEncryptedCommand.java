package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.FormattedComponentArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WhisperEncryptedCommand {

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cwe.playerNotFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwe")
            .then(argument("player", gameProfile(true))
                .then(argument("message", withString(formattedComponent()))
                    .executes((ctx) -> whisper(ctx.getSource(), getSingleProfileArgument(ctx, "player"), getWithString(ctx, "message", MutableComponent.class))))));
    }

    private static int whisper(FabricClientCommandSource source, GameProfile player, Result<MutableComponent> result) throws CommandSyntaxException {
        PlayerInfo recipient = source.getClient().getConnection().getPlayerInfo(player.getId());
        if (recipient == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        MessageC2CPacket packet = new MessageC2CPacket(source.getClient().getConnection().getLocalGameProfile().getName(), result.string());
        C2CPacketHandler.getInstance().sendPacket(packet, recipient);
        MutableComponent prefix = Component.empty();
        prefix.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal("/cwe").withStyle(ChatFormatting.AQUA));
        prefix.append(Component.literal("]").withStyle(ChatFormatting.DARK_GRAY));
        prefix.append(Component.literal(" "));
        Component component = prefix.append(Component.translatable("c2cpacket.messageC2CPacket.outgoing", recipient.getProfile().getName(), result.value()));
        source.sendFeedback(component);
        return Command.SINGLE_SUCCESS;
    }
}
