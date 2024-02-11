package net.cortex.clientAddon.cracker;

import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.mixin.LegacyRandomSourceAccessor;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;

public class SeedCracker {
    public interface OnCrack {void callback(long seed); }


    public static OnCrack callback;
    public static long[] bits=new long[20];
    public static int expectedItems=0;
    public static LongTask currentTask;
    private static int attemptCount = 0;
    private static final int MAX_ATTEMPTS = 10;

    //returns True on success or false on failer
    private static boolean throwItems()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        player.moveTo(player.getX(), player.getY(), player.getZ(), 0, 90);
        Minecraft.getInstance().getConnection().send(new ServerboundMovePlayerPacket.Rot(0, 90, true)); // point to correct location
        for (int i = 0; i < 20; i++) {
            boolean success = PlayerRandCracker.throwItem();
            if (!success) {
                Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("itemCrack.notEnoughItems").withStyle(ChatFormatting.RED));
                EnchantmentCracker.LOGGER.info("Unable to use rng SeedCracker |not enough items|");
                return false;
            }
        }
        return true;
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

        callback.callback(((LegacyRandomSourceAccessor) rand).getSeed().get());//extract seed and call callback
    }

    public static void crack(OnCrack callback) {
        attemptCount = 1;
        doCrack(callback);
    }

    private static void doCrack(OnCrack Callback){
        callback=Callback;
        ClientCommandHelper.addOverlayMessage(Component.translatable("commands.ccrackrng.retries", attemptCount, MAX_ATTEMPTS), 100);
        if(throwItems())
        {
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKING;
            expectedItems=20;
            if (currentTask == null) {
                currentTask = new SeedCrackTask();
                String taskName = TaskManager.addTask("ccrackrng", currentTask);
                Component message = Component.translatable("commands.ccrackrng.starting")
                        .append(" ")
                        .append(ClientCommandHelper.getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName));
                Minecraft.getInstance().gui.getChat().addMessage(message);
            }
        } else {
            Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
        }
    }

    public static void onEntityCreation(ClientboundAddEntityPacket packet) {
        if (packet.getType() == EntityType.ITEM && Configs.playerCrackState == PlayerRandCracker.CrackState.CRACKING) {
            if (SeedCracker.expectedItems > 0) {
                long rand_val = (long) ((Math.atan2(packet.getZa(), packet.getXa()) + Math.PI) / (Math.PI * 2) * ((float) (1 << 24)));
                long top_bits = rand_val;
                short value = (short) (((top_bits >> (24 - 4)) ^ 0x8L )&0xFL);//INSTEAD OF ^0x8L MAYBE DO +math.pi OR SOMETHING ELSE
                SeedCracker.bits[20-SeedCracker.expectedItems]=(long)value;//could be improved
                SeedCracker.expectedItems--;
                if (SeedCracker.expectedItems == 0) {
                    //if its the last item
                    SeedCracker.attemptCrack();
                }
            }
        }
    }
}
