package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.CCNetworkHandler;
import net.earthcomputer.clientcommands.c2c.packets.DiceRollC2CPackets;
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

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class DiceRollCommand {
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

    private static final Map<String, byte[]> opponentHash = new HashMap<>();
    private static final Map<String, Integer> rollSides = new HashMap<>();
    private static final Map<String, BigInteger> playerAB = new HashMap<>();
    private static final Map<String, BigInteger> result = new HashMap<>();

    private static final SecureRandom random = new SecureRandom();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ccoinflip")
            .executes(ctx -> localDiceroll(ctx.getSource(), 2))
            .then(argument("player", gameProfile())
                .executes(ctx -> diceroll(ctx.getSource(), getCProfileArgument(ctx, "player"), 2))));

        dispatcher.register(literal("cdiceroll")
                .executes(ctx -> localDiceroll(ctx.getSource(), 6))
                .then(argument("sides", integer(2, 100))
                        .executes(ctx -> localDiceroll(ctx.getSource(), getInteger(ctx, "sides")))
                        .then(argument("player", gameProfile())
                            .executes(ctx -> diceroll(ctx.getSource(), getCProfileArgument(ctx, "player"), getInteger(ctx, "sides")))))
                .then(argument("player", gameProfile())
                        .executes(ctx -> diceroll(ctx.getSource(), getCProfileArgument(ctx, "player"), 6))));

    }

    private static int localDiceroll(FabricClientCommandSource source, int sides) {
        if (sides == 2) {
            source.sendFeedback(random.nextBoolean() ? Text.translatable("commands.diceroll.heads") : Text.translatable("commands.diceroll.tails"));
        } else {
            source.sendFeedback(Text.literal(Integer.toString(random.nextInt(sides) + 1)));
        }
        return Command.SINGLE_SUCCESS;
    }

    public static byte[] toSHA1(byte[] convertme) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch(NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return md.digest(convertme);
    }

    private static int diceroll(FabricClientCommandSource source, Collection<GameProfile> profiles, int sides) throws CommandSyntaxException {
        assert source.getClient().getNetworkHandler() != null;
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        PlayerListEntry recipient = source.getClient().getNetworkHandler().getPlayerList().stream()
                .filter(p -> p.getProfile().getName().equalsIgnoreCase(profiles.iterator().next().getName()))
                .findFirst()
                .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);

        if (recipient.getProfile().getName().equals(source.getClient().getNetworkHandler().getProfile().getName())) {
            return localDiceroll(source, sides);
        }

        BigInteger a = new BigInteger(729, random).abs();
        BigInteger A = g.modPow(a, p);
        playerAB.put(recipient.getProfile().getName(), a);
        rollSides.put(recipient.getProfile().getName(), sides);

        DiceRollC2CPackets.DiceRollInitC2CPacket packet = new DiceRollC2CPackets.DiceRollInitC2CPacket(source.getClient().getNetworkHandler().getProfile().getName(), sides, toSHA1(A.toByteArray()));
        CCNetworkHandler.getInstance().sendPacket(packet, recipient);
        if (sides == 2) {
            source.sendFeedback(Text.translatable("commands.diceroll.sent", Text.translatable("commands.diceroll.coinflip"), recipient.getProfile().getName()));
        } else {
            source.sendFeedback(Text.translatable("commands.diceroll.sent", Text.translatable("commands.diceroll.diceroll", sides), recipient.getProfile().getName()));
        }
        return Command.SINGLE_SUCCESS;
    }

    public static String byteArrayToHexString(byte[] b) {
        StringBuilder result = new StringBuilder();
        for (byte value : b) {
            result.append(Integer.toString((value & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }


    public static void initCoinflip(DiceRollC2CPackets.DiceRollInitC2CPacket packet) throws CommandSyntaxException {

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
            rollSides.put(packet.sender, packet.sides);

            DiceRollC2CPackets.DiceRollInitC2CPacket response = new DiceRollC2CPackets.DiceRollInitC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), packet.sides, toSHA1(B.toByteArray()));
            CCNetworkHandler.getInstance().sendPacket(response, sender);
            MutableText message;
            if (packet.sides == 2) {
                message = Text.translatable("commands.diceroll.received", Text.translatable("commands.diceroll.coinflip"), packet.sender);
            } else {
                message = Text.translatable("commands.diceroll.recieved", Text.translatable("commands.diceroll.diceroll", packet.sides), packet.sender);
            }
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
        }

        BigInteger b = playerAB.get(packet.sender);
        BigInteger B = g.modPow(b, p);

        DiceRollC2CPackets.DiceRollAcceptedC2CPacket response = new DiceRollC2CPackets.DiceRollAcceptedC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), B);
        CCNetworkHandler.getInstance().sendPacket(response, sender);
    }

    public static void acceptCoinflip(DiceRollC2CPackets.DiceRollAcceptedC2CPacket packet) throws CommandSyntaxException {
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
            MutableText message = Text.translatable("commands.diceroll.cheater", packet.sender).formatted(Formatting.RED);
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            opponentHash.remove(packet.sender);
            playerAB.remove(packet.sender);
            rollSides.remove(packet.sender);
            return;
        }
        BigInteger s = B.modPow(a, p);
        result.put(packet.sender, s);

        DiceRollC2CPackets.DiceRollResultC2CPacket response = new DiceRollC2CPackets.DiceRollResultC2CPacket(MinecraftClient.getInstance().getNetworkHandler().getProfile().getName(), s);
        CCNetworkHandler.getInstance().sendPacket(response, sender);
        opponentHash.remove(packet.sender);
        playerAB.remove(packet.sender);
    }

    public static void completeCoinflip(DiceRollC2CPackets.DiceRollResultC2CPacket packet) {
        if (result.containsKey(packet.sender)) {
            if (result.get(packet.sender).equals(packet.s)) {
                LOGGER.info("Coinflip val: " + packet.s.toString(16));
                MutableText message;
                if (rollSides.get(packet.sender) == 2) {
                    MutableText headstails;
                    BigInteger half = p.divide(BigInteger.valueOf(2));
                    if (packet.s.compareTo(half) > 0) {
                        headstails = Text.translatable("commands.diceroll.heads");
                    } else {
                        headstails = Text.translatable("commands.diceroll.tails");
                    }
                    message = Text.translatable("commands.diceroll.value", Text.translatable("commands.diceroll.coinflip"), packet.sender, headstails);
                } else {
                    BigInteger sideVal = p.divide(BigInteger.valueOf(rollSides.get(packet.sender)));
                    int side = packet.s.divide(sideVal).intValue() + 1;
                    message = Text.translatable("commands.diceroll.value", Text.translatable("commands.diceroll.diceroll", rollSides.get(packet.sender)), packet.sender, side);
                }
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            } else {
                LOGGER.info("expected: " + result.get(packet.sender).toString(16));
                LOGGER.info("actual: " + packet.s.toString(16));
                MutableText message = Text.translatable("commands.diceroll.cheater", packet.sender).formatted(Formatting.RED);
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(message);
            }
            result.remove(packet.sender);
            rollSides.remove(packet.sender);
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
