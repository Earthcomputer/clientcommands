package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.TestRandom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Random;

@Mixin(FishingBobberEntity.class)
public abstract class MixinFishingBobberEntity extends Entity {
    @Shadow private int hookCountdown;
    @Shadow private FishingBobberEntity.State state;
    @Shadow private int waitCountdown;
    @Shadow private int fishTravelCountdown;
    @Shadow @Final private int luckOfTheSeaLevel;
    @Unique private int tickCounter;

    public MixinFishingBobberEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (!world.isClient) {
            if (tickCounter == 0) {
                //System.out.println("SERVERSIDE SEED: " + PlayerRandCracker.getSeed(random));
            }
            tickCounter++;
            //((TestRandom) random).dump();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void postTick(CallbackInfo ci) {
        if (!world.isClient) {
            if (hookCountdown > 0) {
                System.out.println("Server fishable: " + tickCounter);
                Random randomCopy = new Random(PlayerRandCracker.getSeed(random) ^ 0x5deece66dL);
                LootContext.Builder builder = (new LootContext.Builder((ServerWorld)this.world)).parameter(LootContextParameters.ORIGIN, this.getPos()).parameter(LootContextParameters.TOOL, MinecraftClient.getInstance().player.getMainHandStack()).parameter(LootContextParameters.THIS_ENTITY, this).random(randomCopy).luck((float)this.luckOfTheSeaLevel);
                LootTable lootTable = this.world.getServer().getLootManager().getTable(LootTables.FISHING_GAMEPLAY);
                List<ItemStack> list = lootTable.generateLoot(builder.build(LootContextTypes.FISHING));
                System.out.println("Server loot from seed " + PlayerRandCracker.getSeed(random) + ": " + list);
            }
            //System.out.println("Server: " + state + " " + tickCounter + " " + waitCountdown + " " + fishTravelCountdown);
        }
    }
}
