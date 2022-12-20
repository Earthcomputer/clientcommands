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

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

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

    // TODO: make these clear old failed ones to not leak memory

    // hash's of opponent's ABs
    private static final Map<String, byte[]> opponentHash = new HashMap<>();

    //  oppoent name -> your AB
    private static final Map<String, BigInteger> playerAB = new HashMap<>();

    // result to compare with opponent's
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

    public static byte[] toSHA1(byte[] convertme) {
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch(NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return md.digest(convertme);
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

        if (recipient.getProfile().getName().equals(source.getClient().getNetworkHandler().getProfile().getName())) {
            return localCoinflip(source);
        }

        BigInteger a = new BigInteger(729, random).abs();
        BigInteger A = g.modPow(a, p);
        playerAB.put(recipient.getProfile().getName(), a);

        CoinflipC2CPackets.CoinflipInitC2CPacket packet = new CoinflipC2CPackets.CoinflipInitC2CPacket(source.getClient().getNetworkHandler().getProfile().getName(), toSHA1(A.toByteArray()));
        CCNetworkHandler.getInstance().sendPacket(packet, recipient);
        MutableText message = Text.translatable("commands.coinflip.sent", recipient.getProfile().getName());
        source.sendFeedback(message);
        return Command.SINGLE_SUCCESS;
    }

    public static String byteArrayToHexString(byte[] b) {
        StringBuilder result = new StringBuilder();
        for (byte value : b) {
            result.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }


    public static void initCoinflip(CoinflipC2CPackets.CoinflipInitC2CPacket packet) throws CommandSyntaxException {

        // get sender from name
        PlayerListEntry sender = MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(packet.sender))
                .findFirst()
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        opponentHash.put(packet.sender, packet.ABHash);

        if (!playerAB.containsKey(packet.sender)) {
            BigInteger b = new BigInteger(729, random).abs();
            BigInteger B = g.modPow(b, p);
            playerAB.put(packet.sender, b);

            CoinflipC2CPackets.CoinflipInitC2CPacket response = new CoinflipC2CPackets.CoinflipInitC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), toSHA1(B.toByteArray()));
            CCNetworkHandler.getInstance().sendPacket(response, sender);
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.translatable("commands.coinflip.received", packet.sender));
        }

        BigInteger b = playerAB.get(packet.sender);
        BigInteger B = g.modPow(b, p);

        CoinflipC2CPackets.CoinflipAcceptedC2CPacket response = new CoinflipC2CPackets.CoinflipAcceptedC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), B);
        CCNetworkHandler.getInstance().sendPacket(response, sender);
    }

    public static void acceptCoinflip(CoinflipC2CPackets.CoinflipAcceptedC2CPacket packet) throws CommandSyntaxException {
        if (!opponentHash.containsKey(packet.sender)) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        // get sender from name
        PlayerListEntry sender = MinecraftClient.getInstance().getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(packet.sender))
                .findFirst()
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        BigInteger a = playerAB.get(packet.sender);
        BigInteger B = packet.AB;

        // check if hash matches
        if (!Arrays.equals(opponentHash.get(packet.sender), toSHA1(B.toByteArray()))) {
            System.out.println("expected: " + byteArrayToHexString(opponentHash.get(packet.sender)));
            System.out.println("actual: " + byteArrayToHexString(toSHA1(B.toByteArray())));
            MutableText message = Text.translatable("commands.coinflip.cheater", packet.sender).formatted(Formatting.RED);
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            opponentHash.remove(packet.sender);
            playerAB.remove(packet.sender);
            return;
        }
        BigInteger s = B.modPow(a, p);
        result.put(packet.sender, s);

        CoinflipC2CPackets.CoinflipResultC2CPacket response = new CoinflipC2CPackets.CoinflipResultC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), s);
        CCNetworkHandler.getInstance().sendPacket(response, sender);
        opponentHash.remove(packet.sender);
        playerAB.remove(packet.sender);
    }

    public static void completeCoinflip(CoinflipC2CPackets.CoinflipResultC2CPacket packet) {
        if (result.containsKey(packet.sender)) {
            if (result.get(packet.sender).equals(packet.s)) {
                LOGGER.info("Coinflip val: " + packet.s.toString(16));
                MutableText message = Text.translatable("commands.coinflip.value", packet.sender, Text.translatable(packet.s.mod(BigInteger.TWO).equals(BigInteger.ONE) ? "commands.coinflip.heads" : "commands.coinflip.tails"));
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            } else {
                LOGGER.info("expected: " + result.get(packet.sender).toString(16));
                LOGGER.info("actual: " + packet.s.toString(16));
                MutableText message = Text.translatable("commands.coinflip.cheater", packet.sender).formatted(Formatting.RED);
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            }
            result.remove(packet.sender);
        } else {
            throw new IllegalStateException("Coinflip result packet received before accepted/init packet");
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
