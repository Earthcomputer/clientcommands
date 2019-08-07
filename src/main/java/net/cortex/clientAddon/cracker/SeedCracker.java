package net.cortex.clientAddon.cracker;

import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.MessageType;
import net.minecraft.server.network.packet.PlayerActionC2SPacket;
import net.minecraft.server.network.packet.PlayerMoveC2SPacket;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

import static net.earthcomputer.clientcommands.command.CrackPlayerRNGUsingItemsCommand.padLeftZeros;
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
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if ( minecraft.player.inventory.getMainHandStack().getCount()>19) {//check that have at least 19 items in current hand (doesnt seem to work)
            System.out.println("hand item count: "+(minecraft.player.inventory.getMainHandStack().getCount()));
            ClientPlayNetworkHandler networkHandler = minecraft.getNetworkHandler();
            networkHandler.sendPacket(new PlayerMoveC2SPacket.LookOnly(0.0f, 90.0f, true)); //point to correct location
            for (int i = 0; i < 20; i++)//drop 20 items + test items+13;
                networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM, BlockPos.ORIGIN, Direction.DOWN));
        }
        else {
            minecraft.inGameHud.addChatMessage(MessageType.CHAT.GAME_INFO, new LiteralText(Formatting.RED+"Unable to use rng SeedCracker |not enough items in player hand|"));
            System.out.println("Unable to use rng SeedCracker |not enough items|");
            return false;
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
}
