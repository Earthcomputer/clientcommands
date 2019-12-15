package net.earthcomputer.clientcommands.interfaces;

import net.minecraft.entity.damage.DamageSource;

public interface ILivingEntity {

    float getLastDamageTaken();

    boolean callBlockedByShield(DamageSource damageSource);

}
