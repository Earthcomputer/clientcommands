package net.cortex.clientAddon.cracker;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.mixin.LegacyRandomSourceAccessor;
import net.earthcomputer.clientcommands.task.ItemThrowTask;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.RandomSource;

public class SeedCracker {
    public interface OnCrack {void callback(long seed); }


    public static OnCrack callback;
    public static long[] bits=new long[20];
    public static int expectedItems=0;
    public static LongTask currentTask;
    private static int attemptCount = 0;
    private static final int MAX_ATTEMPTS = 10;
    private static String currentTaskName = null;

    private static String throwItems()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        player.moveTo(player.getX(), player.getY(), player.getZ(), 0, 90);
        Minecraft.getInstance().getConnection().send(new ServerboundMovePlayerPacket.Rot(0, 90, true)); // point to correct location
        ItemThrowTask task = new ItemThrowTask(20) {
            @Override
            protected void onSuccess() {
                SeedCracker.attemptCrack();
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

    public static void attemptCrack()
    {
        long seed= Lattice_cracker.crack(SeedCracker.bits);

        if(seed==0)//Basicaly if seed is zero it means it failed to try to crack again
        {
            attemptCount++;
            if (attemptCount > MAX_ATTEMPTS) {
                ClientCommandHelper.sendError(Component.translatable("commands.ccrackrng.failed"));
                ClientCommandHelper.sendHelp(Component.translatable("commands.ccrackrng.failed.help"));
                Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
                currentTaskName = null;
            } else {
                SeedCracker.doCrack(SeedCracker.callback);
            }
            return;
        }
        //Else, got a seed

        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;

        RandomSource rand=RandomSource.create(seed ^ 0x5deece66dL);
        rand.nextFloat();
        rand.nextFloat();
        //rand.nextFloat();

        /*
		for(int i=0;i<13;i++) {
			long x = (((long) (rand.nextFloat() * ((float) (1 << 24)))) >> (24 - 4))&0xFL;
			System.out.print("Expected: "+padLeftZeros(Long.toBinaryString(x), 4)+" ");
			System.out.print(padLeftZeros(Long.toBinaryString((((long) (rand.nextFloat() * ((float) (1 << 24)))) >> (24 - 4))&0xFL), 4)+" ");
			System.out.print(padLeftZeros(Long.toBinaryString((((long) (rand.nextFloat() * ((float) (1 << 24)))) >> (24 - 4))&0xFL), 4)+" ");
			System.out.print(padLeftZeros(Long.toBinaryString((((long) (rand.nextFloat() * ((float) (1 << 24)))) >> (24 - 4))&0xFL), 4)+" \n");
		}*/

        currentTaskName = null;
        callback.callback(((LegacyRandomSourceAccessor) rand).getSeed().get());//extract seed and call callback
    }

    public static void crack(OnCrack callback) {
        attemptCount = 1;
        doCrack(callback);
    }

    private static void doCrack(OnCrack Callback){
        callback=Callback;
        ClientCommandHelper.addOverlayMessage(Component.translatable("commands.ccrackrng.retries", attemptCount, MAX_ATTEMPTS), 100);
        currentTaskName = throwItems();
        Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKING;
        expectedItems = 20;
        if (attemptCount == 1) {
            Component message = Component.translatable("commands.ccrackrng.starting")
                .append(" ")
                .append(ClientCommandHelper.getCommandTextComponent("commands.client.cancel", "/ctask stop " + currentTaskName));
            Minecraft.getInstance().gui.getChat().addMessage(message);
        }
    }

    public static void onEntityCreation(ClientboundAddEntityPacket packet) {
        if (Configs.playerCrackState == PlayerRandCracker.CrackState.CRACKING) {
            if (SeedCracker.expectedItems > 0) {
                long rand_val = (long) ((Math.atan2(packet.getZa(), packet.getXa()) + Math.PI) / (Math.PI * 2) * ((float) (1 << 24)));
                long top_bits = rand_val;
                short value = (short) (((top_bits >> (24 - 4)) ^ 0x8L )&0xFL);//INSTEAD OF ^0x8L MAYBE DO +math.pi OR SOMETHING ELSE
                SeedCracker.bits[20-SeedCracker.expectedItems]=(long)value;//could be improved
                SeedCracker.expectedItems--;
            }
        }
    }
}
