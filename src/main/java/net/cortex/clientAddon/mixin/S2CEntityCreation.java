package net.cortex.clientAddon.mixin;


import net.cortex.clientAddon.cracker.SeedCracker;
import net.cortex.clientAddon.cracker.Lattice_cracker;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.minecraft.client.network.packet.EntitySpawnS2CPacket;
import net.minecraft.entity.EntityType;
import net.minecraft.util.PacketByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static net.earthcomputer.clientcommands.features.EnchantmentCracker.MULTIPLIER;

@Mixin(EntitySpawnS2CPacket.class)
public abstract class S2CEntityCreation {
    @Shadow
    public abstract double getVelocityz();

    @Shadow
    public abstract double getVelocityX();

    @Shadow
    public abstract int getId();

    @Shadow
    public abstract EntityType<?> getEntityTypeId();

    @Inject(at = @At("TAIL"), method = "read")
    public void read(PacketByteBuf packetByteBuf_1, CallbackInfo ci) throws IOException
    {
        if(this.getEntityTypeId() == EntityType.ITEM && SeedCracker.expectedItems>0) {

            long rand_val = (long) ((Math.atan2(this.getVelocityz(), this.getVelocityX()) + Math.PI) / (Math.PI * 2) * ((float) (1 << 24)));
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









    public String padLeftZeros(String inputString, int length) {
        if (inputString.length() >= length) {
            return inputString;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length - inputString.length()) {
            sb.append('0');
        }
        sb.append(inputString);

        return sb.toString();
    }
}
