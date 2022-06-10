package net.earthcomputer.clientcommands.mixin;

import com.google.common.base.Objects;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.interfaces.ILivingEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity implements ILivingEntity {

    @Shadow private BlockPos lastBlockPos;

    @Shadow protected abstract boolean isOnSoulSpeedBlock();

    @Shadow protected int itemUseTimeLeft;

    public MixinLivingEntity(EntityType<?> entityType_1, World world_1) {
        super(entityType_1, world_1);
    }

    @Inject(method = "tickCramming", at = @At("HEAD"))
    public void onEntityCramming(CallbackInfo ci) {
        if (isThePlayer() && world.getOtherEntities(this, getBoundingBox(), Entity::isPushable).size() >= 24) {
            PlayerRandCracker.onEntityCramming();
        }
    }

    @Inject(method = "spawnConsumptionEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getDrinkSound(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/sound/SoundEvent;"))
    public void onDrink(CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onDrink();
    }

    @Inject(method = "spawnConsumptionEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getEatSound(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/sound/SoundEvent;"))
    public void onEat(ItemStack stack, int particleCount, CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onEat(stack, this.getPos(), particleCount, this.itemUseTimeLeft);
    }

    @Inject(method = "baseTick",
            slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/tag/FluidTags;WATER:Lnet/minecraft/tag/TagKey;", ordinal = 0)),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;", ordinal = 0))
    public void onUnderwater(CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onUnderwater();
    }

    @Inject(method = "playEquipmentBreakEffects", at = @At("HEAD"))
    public void onEquipmentBreak(ItemStack stack, CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onEquipmentBreak();
    }

    @Inject(method = "tickStatusEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isInvisible()Z"))
    public void onPotionParticles(CallbackInfo ci) {
        if (isThePlayer())
            PlayerRandCracker.onPotionParticles();
    }

    @Inject(method = "baseTick", at = @At(value = "FIELD", target = "Lnet/minecraft/world/World;isClient:Z", ordinal = 2))
    public void testFrostWalker(CallbackInfo ci) {
        if (!isThePlayer())
            return;

        BlockPos pos = getBlockPos();
        if (!Objects.equal(pos, this.lastBlockPos)) {
            this.lastBlockPos = pos;
            if (onGround) {
                int frostWalkerLevel = EnchantmentHelper.getEquipmentLevel(Enchantments.FROST_WALKER, (LivingEntity) (Object) this);
                if (frostWalkerLevel > 0) {
                    BlockState frostedIce = Blocks.FROSTED_ICE.getDefaultState();
                    float radius = Math.min(16, frostWalkerLevel + 2);
                    for (BlockPos offsetPos : BlockPos.iterate(pos.add(-radius, -1, -radius), pos.add(radius, -1, radius))) {
                        BlockState offsetState = world.getBlockState(offsetPos);
                        if (offsetState.getMaterial() == Material.WATER && offsetState.get(FluidBlock.LEVEL) == 0 && world.canPlace(frostedIce, offsetPos, ShapeContext.absent())) {
                            if (world.isAir(offsetPos.up())) {
                                PlayerRandCracker.onFrostWalker();
                            }
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;shouldDisplaySoulSpeedEffects()Z"))
    private void testSoulSpeed(CallbackInfo ci) {
        if (!isThePlayer())
            return;

        boolean hasSoulSpeed = EnchantmentHelper.hasSoulSpeed((LivingEntity) (Object) this);
        if (hasSoulSpeed && isOnSoulSpeedBlock()) {
            PlayerRandCracker.onSoulSpeed();
        }
    }

    private boolean isThePlayer() {
        //noinspection ConstantConditions
        return (Object) this instanceof ClientPlayerEntity;
    }

    @Accessor
    @Override
    public abstract float getLastDamageTaken();

    @Invoker
    @Override
    public abstract boolean callBlockedByShield(DamageSource damageSource);
}
