package net.earthcomputer.clientcommands.interfaces;

import com.mojang.datafixers.util.Pair;
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

    void clientcommands_onAmbientSoundPlayed(float pitch);

    void clientcommands_onNoSoundPlayed(float pitch);

    void clientcommands_onSplashSoundPlayed(float pitch);

    void clientcommands_onServerTick();

    Pair<Integer, VillagerCommand.Offer> clientcommands_bruteForceOffers(VillagerTrades.ItemListing[] listings, VillagerProfession profession, int maxCalls, Predicate<VillagerCommand.Offer> predicate);
}
