package net.cortex.clientAddon.cracker;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.packet.EntitySpawnS2CPacket;
import net.minecraft.entity.EntityType;
import net.minecraft.network.MessageType;
import net.minecraft.server.network.packet.PlayerMoveC2SPacket;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;

import java.util.Random;
import static net.earthcomputer.clientcommands.features.EnchantmentCracker.MULTIPLIER;

public class SeedCracker {
    public interface OnCrack {void callback(long seed); }


    public static OnCrack callback;
    public static long[] bits=new long[20];
    public static int expectedItems=0;
    public static boolean cracking=false;

    //returns True on success or false on failer
    private static boolean throwItems()
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        player.setPositionAndAngles(player.x, player.y, player.z, 0, 90);
        MinecraftClient.getInstance().getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookOnly(0, 90, true)); // point to correct location
        for (int i = 0; i < 20; i++) {
            EnchantmentCracker.EnchantManipulationStatus status = EnchantmentCracker.throwItem();
            if (status != EnchantmentCracker.EnchantManipulationStatus.OK && status != EnchantmentCracker.EnchantManipulationStatus.NOT_CRACKED) {
                MinecraftClient.getInstance().inGameHud.addChatMessage(MessageType.GAME_INFO, new TranslatableText("itemCrack.notEnoughItems").formatted(Formatting.RED));
                EnchantmentCracker.LOGGER.info("Unable to use rng SeedCracker |not enough items|");
                return false;
            }
        }
        return true;
    }
	public static void attemptCrack()
	{
		cracking=false;
		long seed= Lattice_cracker.crack(SeedCracker.bits);

		if(seed==0)//Basicaly if seed is zero it means it failed to try to crack again
		{
			SeedCracker.crack(SeedCracker.callback);
			return;
		}
		//Else, got a seed
		
		Random rand=new Random();
		rand.setSeed(seed ^ MULTIPLIER);
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

		callback.callback(EnchantmentCracker.getSeed(rand));//extract seed and call callback
	}
    public static void crack(OnCrack Callback){
        callback=Callback;
        if(throwItems())
        {
            cracking=true;
            expectedItems=20;
        }
    }

    public static void onEntityCreation(EntitySpawnS2CPacket packet) {
        if (packet.getEntityTypeId() == EntityType.ITEM && SeedCracker.expectedItems>0) {

            long rand_val = (long) ((Math.atan2(packet.getVelocityz(), packet.getVelocityX()) + Math.PI) / (Math.PI * 2) * ((float) (1 << 24)));
            long top_bits = rand_val;
            short value = (short) (((top_bits >> (24 - 4)) ^ 0x8L )&0xFL);//INSTEAD OF ^0x8L MAYBE DO +math.pi OR SOMETHING ELSE
            SeedCracker.bits[20-SeedCracker.expectedItems]=(long)value;//could be improved
            SeedCracker.expectedItems--;
        }
        if(SeedCracker.expectedItems==0&&SeedCracker.cracking)//if its the last item
        {
            SeedCracker.attemptCrack();
        }
        /*
        else
        {
            long rand_val = (long) ((Math.atan2(this.getVelocityz(), this.getVelocityX()) + Math.PI) / (Math.PI * 2) * ((float) (1 << 24)));
            long top_bits = rand_val;
            short value = (short) ((top_bits >> (24 - 4)) ^ 0x8L);
            System.out.println("Entity item spawn: Top 4 bits of direction: "+padLeftZeros(Long.toBinaryString(value), 4));
        }*/
    }
}
