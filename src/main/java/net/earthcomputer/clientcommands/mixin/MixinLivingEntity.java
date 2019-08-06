package net.earthcomputer.clientcommands.mixin;

import com.google.common.base.Objects;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.interfaces.ILivingEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.Material;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements ILivingEntity {

    @Shadow private BlockPos lastBlockPos;

    public MixinLivingEntity(EntityType<?> entityType_1, World world_1) {
        super(entityType_1, world_1);
    }

    @Inject(method = "tickPushing", at = @At("HEAD"))
    public void onEntityCramming(CallbackInfo ci) {
        if (isThePlayer() && world.getEntities(this, getBoundingBox(), Entity::isPushable).size() >= 24) {
            EnchantmentCracker.onEntityCramming();
        }
    }

    @Inject(method = "spawnConsumptionEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getDrinkSound(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/sound/SoundEvent;"))
    public void onDrink(CallbackInfo ci) {
        if (isThePlayer())
            EnchantmentCracker.onDrink();
    }

    @Inject(method = "spawnConsumptionEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getEatSound(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/sound/SoundEvent;"))
    public void onEat(CallbackInfo ci) {
        if (isThePlayer())
            EnchantmentCracker.onEat();
    }

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    public void onUnderwater(CallbackInfo ci) {
        if (isThePlayer())
            EnchantmentCracker.onUnderwater();
    }

    @Inject(method = "playEquipmentBreakEffects", at = @At("HEAD"))
    public void onEquipmentBreak(ItemStack stack, CallbackInfo ci) {
        if (isThePlayer())
            EnchantmentCracker.onEquipmentBreak();
    }

    @Inject(method = "spawnPotionParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInvisible()Z"))
    public void onPotionParticles(CallbackInfo ci) {
        if (isThePlayer())
            EnchantmentCracker.onPotionParticles();
    }

    @Inject(method = "baseTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;isClient:Z", ordinal = 2))
    public void testFrostWalker(CallbackInfo ci) {
        if (!isThePlayer())
            return;

        BlockPos pos = new BlockPos(this);
        if (!Objects.equal(pos, this.lastBlockPos)) {
            this.lastBlockPos = pos;
            if (onGround) {
                int frostWalkerLevel = EnchantmentHelper.getEquipmentLevel(Enchantments.FROST_WALKER, (LivingEntity) (Object) this);
                if (frostWalkerLevel > 0) {
                    BlockState frostedIce = Blocks.FROSTED_ICE.getDefaultState();
                    float radius = Math.min(16, frostWalkerLevel + 2);
                    for (BlockPos offsetPos : BlockPos.iterate(pos.add(-radius, -1, -radius), pos.add(radius, -1, radius))) {
                        BlockState offsetState = world.getBlockState(offsetPos);
                        if (offsetState.getMaterial() == Material.WATER && offsetState.get(FluidBlock.LEVEL) == 0 && world.canPlace(frostedIce, offsetPos, EntityContext.absent())) {
                            if (world.isAir(offsetPos.up())) {
                                EnchantmentCracker.onFrostWalker();
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isThePlayer() {
        //noinspection ConstantConditions
        return (Object) this instanceof ClientPlayerEntity;
    }

    @Accessor("field_6253")
    @Override
    public abstract float getLastDamage();
}
