package net.earthcomputer.clientcommands.mixin.commands.villager;

import net.earthcomputer.clientcommands.features.VillagerRngSimulator;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Villager.class)
public class VillagerMixin implements IVillager {
    @Unique
    VillagerRngSimulator rng = new VillagerRngSimulator(null, 0);

    @Unique
    boolean hasSetCrackedAmbientSoundTime = false;

    @Override
    public void clientcommands_setCrackedRandom(RandomSource random) {
        rng = new VillagerRngSimulator((LegacyRandomSource) random, rng.getAmbientSoundTime());
        hasSetCrackedAmbientSoundTime = false;
    }

    @Override
    public VillagerRngSimulator clientcommands_getCrackedRandom() {
        return rng;
    }

    @Override
    public void clientcommands_onAmbientSoundPlayed() {
        if (!hasSetCrackedAmbientSoundTime) {
            rng.onAmbientSoundPlayed();
        }
    }

    @Override
    public void clientcommands_onServerTick() {
        if (rng.simulateTick()) {
            hasSetCrackedAmbientSoundTime = true;
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("hrmm"));
        }
    }
}
