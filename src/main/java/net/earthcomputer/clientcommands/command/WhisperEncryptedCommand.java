package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.CCNetworkHandler;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.gameProfile;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.getCProfileArgument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class WhisperEncryptedCommand {

    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwe.playerNotFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwe")
                .then(argument("player", gameProfile())
                    .then(argument("message", greedyString())
                        .executes((ctx) -> whisper(ctx.getSource(), getCProfileArgument(ctx, "player"), getString(ctx, "message"))))));
    }

    private static int whisper(FabricClientCommandSource source, Collection<GameProfile> profiles, String message) throws CommandSyntaxException {
        assert source.getClient().getNetworkHandler() != null;
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        PlayerListEntry recipient = CCNetworkHandler.getPlayerByName(profiles.iterator().next().getName())
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        MessageC2CPacket packet = new MessageC2CPacket(source.getClient().getNetworkHandler().getProfile().getName(), message);
        CCNetworkHandler.getInstance().sendPacket(packet, recipient);
        MutableText prefix = Text.empty();
        prefix.append(Text.literal("[").formatted(Formatting.DARK_GRAY));
        prefix.append(Text.literal("/cwe").formatted(Formatting.AQUA));
        prefix.append(Text.literal("]").formatted(Formatting.DARK_GRAY));
        prefix.append(Text.literal(" "));
        Text text = prefix.append(Text.translatable("ccpacket.messageC2CPacket.outgoing", recipient.getProfile().getName(), message).formatted(Formatting.GRAY));
        source.sendFeedback(text);
        return Command.SINGLE_SUCCESS;
    }
}
