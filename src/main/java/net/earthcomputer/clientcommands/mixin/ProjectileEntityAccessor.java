package net.earthcomputer.clientcommands.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Projectile.class)
public interface ProjectileEntityAccessor {
    @Invoker("canHitEntity")
    boolean callCanHit(Entity entity);
}
