package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.CCNetworkHandler;
import net.earthcomputer.clientcommands.c2c.packets.CoinflipC2CPackets;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.gameProfile;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.getCProfileArgument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CoinflipCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwe.playerNotFound"));

    private static final BigInteger p = new BigInteger(1,
            intsToBytes(new int[] {
                    0xFFFFFFFF, 0xFFFFFFFF, 0xC90FDAA2, 0x2168C234, 0xC4C6628B, 0x80DC1CD1,
                    0x29024E08, 0x8A67CC74, 0x020BBEA6, 0x3B139B22, 0x514A0879, 0x8E3404DD,
                    0xEF9519B3, 0xCD3A431B, 0x302B0A6D, 0xF25F1437, 0x4FE1356D, 0x6D51C245,
                    0xE485B576, 0x625E7EC6, 0xF44C42E9, 0xA63A3620, 0xFFFFFFFF, 0xFFFFFFFF
            })
    );

    private static final BigInteger g = BigInteger.TWO;

    //TODO: make these clear old failed ones to not leak memory
    private static final Map<String, BigInteger> initializedCoinflips = new HashMap<>();
    private static final Map<String, BigInteger> result = new HashMap<>();

    private static final SecureRandom random = new SecureRandom();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccoinflip")
            .executes(ctx -> localCoinflip(ctx.getSource()))
            .then(argument("player", gameProfile())
                .executes(ctx -> coinflip(ctx.getSource(), getCProfileArgument(ctx, "player")))));
    }

    private static int localCoinflip(FabricClientCommandSource source) {
        source.sendFeedback(random.nextBoolean() ? Text.translatable("commands.coinflip.heads") : Text.translatable("commands.coinflip.tails"));
        return Command.SINGLE_SUCCESS;
    }

    private static int coinflip(FabricClientCommandSource source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        assert source.getClient().getNetworkHandler() != null;
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        PlayerListEntry recipient = source.getClient().getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(profiles.iterator().next().getName()))
                .findFirst()
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        BigInteger a = new BigInteger(729, random).abs();

        BigInteger A = g.modPow(a, p);

        CoinflipC2CPackets.CoinflipInitC2CPacket packet = new CoinflipC2CPackets.CoinflipInitC2CPacket(source.getClient().getNetworkHandler().getProfile().getName(), A);
        CCNetworkHandler.getInstance().sendPacket(packet, recipient);
        initializedCoinflips.put(recipient.getProfile().getName(), a);
        MutableText message = Text.translatable("commands.coinflip.sent", recipient.getProfile().getName());
        source.sendFeedback(message);
        return Command.SINGLE_SUCCESS;
    }

    public static void acceptCoinflip(CoinflipC2CPackets.CoinflipInitC2CPacket packet) throws CommandSyntaxException {
        if (!initializedCoinflips.containsKey(packet.sender)) {
            BigInteger b = new BigInteger(729, random).abs();
            BigInteger B = g.modPow(b, p);

            BigInteger s = packet.AB.modPow(b, p);
            result.put(packet.sender, s);

            CoinflipC2CPackets.CoinflipInitC2CPacket response = new CoinflipC2CPackets.CoinflipInitC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), B);

            // get sender from name
            PlayerListEntry sender = MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                    .filter(p -> p.getProfile().getName().equalsIgnoreCase(packet.sender))
                    .findFirst()
                    .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

            CCNetworkHandler.getInstance().sendPacket(response, sender);
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable("commands.coinflip.received", packet.sender));

            CoinflipC2CPackets.CoinflipResultC2CPacket resultPacket = new CoinflipC2CPackets.CoinflipResultC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), s);
            CCNetworkHandler.getInstance().sendPacket(resultPacket, sender);
        } else {
            BigInteger a = initializedCoinflips.get(packet.sender);
            BigInteger s = packet.AB.modPow(a, p);
            initializedCoinflips.remove(packet.sender);
            result.put(packet.sender, s);

            // get sender from name
            PlayerListEntry sender = MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                    .filter(p -> p.getProfile().getName().equalsIgnoreCase(packet.sender))
                    .findFirst()
                    .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

            CoinflipC2CPackets.CoinflipResultC2CPacket response = new CoinflipC2CPackets.CoinflipResultC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), s);
            CCNetworkHandler.getInstance().sendPacket(response, sender);
        }
    }

    public static void completeCoinflip(CoinflipC2CPackets.CoinflipResultC2CPacket packet) {
        if (result.containsKey(packet.sender)) {
            if (result.get(packet.sender).equals(packet.s)) {
                LOGGER.info("Coinflip val: " + packet.s.toString(16));
                MutableText message = Text.translatable("commands.coinflip.value", packet.sender, Text.translatable(packet.s.mod(BigInteger.TWO).equals(BigInteger.ONE) ? "commands.coinflip.heads" : "commands.coinflip.tails"));
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            } else {
                LOGGER.info("Coinflip val: " + result.get(packet.sender).toString(16));
                LOGGER.info("Remote val: " + packet.s.toString(16));
                MutableText message = Text.translatable("commands.coinflip.cheater", packet.sender).formatted(Formatting.RED);
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            }
            result.remove(packet.sender);
        } else {
            throw new IllegalStateException("Coinflip result packet received before init packet");
        }
    }

    private static byte[] intsToBytes(int[] ints) {
        ByteBuffer buffer = ByteBuffer.allocate(ints.length * Integer.BYTES);
        for (int i : ints) {
            buffer.putInt(i);
        }
        return buffer.array();
    }
}
