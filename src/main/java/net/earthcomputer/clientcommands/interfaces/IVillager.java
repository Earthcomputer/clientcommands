package net.earthcomputer.clientcommands.interfaces;

import net.earthcomputer.clientcommands.command.VillagerCommand;
import net.earthcomputer.clientcommands.features.VillagerRngSimulator;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface IVillager {
    void clientcommands_setCrackedRandom(@Nullable RandomSource random);

    VillagerRngSimulator clientcommands_getCrackedRandom();

    void clientcommands_onAmbientSoundPlayed();

    void clientcommands_onServerTick();

    int clientcommands_bruteForceOffers(VillagerTrades.ItemListing[] listings, VillagerProfession profession, int maxTicks, Predicate<VillagerCommand.Offer> predicate);
}
