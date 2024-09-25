package net.earthcomputer.clientcommands.features;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.task.ItemThrowTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class CCrackRng {
    public static final int NUM_THROWS = 10;
    // This value was computed by brute forcing all seeds
    public static final float MAX_ERROR = 0.00883889f;

    @FunctionalInterface
    public interface OnCrack {
        void callback(long seed);
    }


    public static OnCrack callback;
    public static float[] nextFloats = new float[NUM_THROWS];
    public static int expectedItems=0;
    private static int attemptCount = 0;
    private static final int MAX_ATTEMPTS = 5;
    private static String currentTaskName = null;

    private static String throwItems() throws CommandSyntaxException {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        player.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 90);
        player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), 90, true)); // point to correct location
        ItemThrowTask task = new ItemThrowTask(NUM_THROWS) {
            @Override
            protected void onSuccess() {
                try {
                    CCrackRng.attemptCrack();
                } catch (CommandSyntaxException e) {
                    ClientCommandHelper.sendError(ComponentUtils.fromMessage(e.getRawMessage()));
                }
            }

            @Override
            protected void onFailedToThrowItem() {
                Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("itemCrack.notEnoughItems").withStyle(ChatFormatting.RED));
                EnchantmentCracker.LOGGER.info("Unable to use rng SeedCracker |not enough items|");
                Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
                currentTaskName = null;
            }

            @Override
            protected void onItemSpawn(ClientboundAddEntityPacket packet) {
                onEntityCreation(packet);
            }
        };
        if (currentTaskName != null) {
            TaskManager.forceAddTask(currentTaskName, task);
            return currentTaskName;
        } else {
            return TaskManager.addTask("ccrackrng", task);
        }
    }

    public static void attemptCrack() throws CommandSyntaxException {
        long[] seeds = CCrackRngGen.getSeeds(
            Math.max(0, nextFloats[0] - MAX_ERROR), Math.min(1, nextFloats[0] + MAX_ERROR),
            Math.max(0, nextFloats[1] - MAX_ERROR), Math.min(1, nextFloats[1] + MAX_ERROR),
            Math.max(0, nextFloats[2] - MAX_ERROR), Math.min(1, nextFloats[2] + MAX_ERROR),
            Math.max(0, nextFloats[3] - MAX_ERROR), Math.min(1, nextFloats[3] + MAX_ERROR),
            Math.max(0, nextFloats[4] - MAX_ERROR), Math.min(1, nextFloats[4] + MAX_ERROR),
            Math.max(0, nextFloats[5] - MAX_ERROR), Math.min(1, nextFloats[5] + MAX_ERROR),
            Math.max(0, nextFloats[6] - MAX_ERROR), Math.min(1, nextFloats[6] + MAX_ERROR),
            Math.max(0, nextFloats[7] - MAX_ERROR), Math.min(1, nextFloats[7] + MAX_ERROR),
            Math.max(0, nextFloats[8] - MAX_ERROR), Math.min(1, nextFloats[8] + MAX_ERROR),
            Math.max(0, nextFloats[9] - MAX_ERROR), Math.min(1, nextFloats[9] + MAX_ERROR)
        ).toArray();

        if (seeds.length != 1) {
            attemptCount++;
            if (attemptCount > MAX_ATTEMPTS) {
                ClientCommandHelper.sendError(Component.translatable("commands.ccrackrng.failed"));
                ClientCommandHelper.sendHelp(Component.translatable("commands.ccrackrng.failed.help"));
                Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
                currentTaskName = null;
            } else {
                CCrackRng.doCrack(CCrackRng.callback);
            }
            return;
        }

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        callback.callback(seeds[0]);
    }

    public static void crack(OnCrack callback) throws CommandSyntaxException {
        attemptCount = 1;
        doCrack(callback);
    }

    private static void doCrack(OnCrack Callback) throws CommandSyntaxException {
        callback=Callback;
        ClientCommandHelper.addOverlayMessage(Component.translatable("commands.ccrackrng.retries", attemptCount, MAX_ATTEMPTS), 100);
        currentTaskName = throwItems();
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKING;
        expectedItems = NUM_THROWS;
        if (attemptCount == 1) {
            Component message = Component.translatable("commands.ccrackrng.starting")
                .append(" ")
                .append(ClientCommandHelper.getCommandTextComponent("commands.client.cancel", "/ctask stop " + currentTaskName));
            Minecraft.getInstance().gui.getChat().addMessage(message);
        }
    }

    public static void onEntityCreation(ClientboundAddEntityPacket packet) {
        if (Configs.playerCrackState == PlayerRandCracker.CrackState.CRACKING) {
            if (CCrackRng.expectedItems > 0) {
                float nextFloat = (float) Math.sqrt(packet.getXa() * packet.getXa() + packet.getZa() * packet.getZa()) * 50f;
                CCrackRng.nextFloats[NUM_THROWS - CCrackRng.expectedItems] = nextFloat;
                CCrackRng.expectedItems--;
            }
        }
    }
}
