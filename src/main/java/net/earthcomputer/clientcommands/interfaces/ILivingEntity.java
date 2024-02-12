package net.earthcomputer.clientcommands.interfaces;

import net.minecraft.world.damagesource.DamageSource;

public interface ILivingEntity {

    float getLastHurt();

    boolean callIsDamageSourceBlocked(DamageSource damageSource);

}
