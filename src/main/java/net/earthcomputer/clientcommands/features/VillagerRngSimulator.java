package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.VillagerCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class VillagerRngSimulator {
    @Nullable
    private LegacyRandomSource random;
    private int ambientSoundTime;
    private int waitingTicks = 0;
    private boolean madeSound = false;
    private boolean firstAmbientNoise = true;

    public VillagerRngSimulator(@Nullable LegacyRandomSource random, int ambientSoundTime) {
        this.random = random;
        this.ambientSoundTime = ambientSoundTime;
    }

    public VillagerRngSimulator copy() {
        VillagerRngSimulator that = new VillagerRngSimulator(random == null ? null : new LegacyRandomSource(random.seed.get() ^ 0x5deece66dL), ambientSoundTime);
        that.waitingTicks = this.waitingTicks;
        that.madeSound = this.madeSound;
        that.firstAmbientNoise = this.firstAmbientNoise;
        return that;
    }

    public void simulateTick() {
        if (random == null) {
            return;
        }

        simulateBaseTick();
        simulateServerAiStep();
    }

    public void simulateBaseTick() {
        if (waitingTicks > 0) {
            waitingTicks--;
            return;
        }

        if (random == null) {
            return;
        }

        // we have the server receiving ambient noise tell us if we have to do this to increment the random, this is so that our ambient sound time is synced up.
        if (random.nextInt(1000) < ambientSoundTime++ && !firstAmbientNoise) {
            random.nextFloat();
            random.nextFloat();
            ambientSoundTime = -80;
            madeSound = true;
        } else {
            madeSound = false;
        }
    }

    public void simulateServerAiStep() {
        if (random == null) {
            return;
        }

        random.nextInt(100);
    }

    public boolean anyOffersMatch(VillagerTrades.ItemListing[] listings, Entity trader, Predicate<VillagerCommand.Offer> predicate) {
        if (!isCracked()) {
            return false;
        }

        ArrayList<VillagerTrades.ItemListing> newListings = new ArrayList<>(List.of(listings));
        int i = 0;
        while (i < 2 && !newListings.isEmpty()) {
            VillagerTrades.ItemListing listing = newListings.remove(random.nextInt(newListings.size()));
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer != null) {
                if (predicate.test(new VillagerCommand.Offer(offer.getBaseCostA(), offer.getCostB(), offer.getResult()))) {
                    return true;
                } else {
                    i++;
                }
            }
        }
        return false;
    }

    @Nullable
    public LegacyRandomSource getRandom() {
        return random;
    }

    public boolean isCracked() {
        return random != null && !firstAmbientNoise;
    }

    public void setRandom(@Nullable LegacyRandomSource random) {
        this.random = random;
    }

    @Override
    public String toString() {
        return "VillagerRngSimulator[" +
            "seed=" + (random == null ? "null" : random.seed.get()) + ", " +
            "ambientSoundTime=" + ambientSoundTime + ']';
    }

    public void onAmbientSoundPlayed() {
        if (firstAmbientNoise) {
            if (random == null) {
                return;
            }

            firstAmbientNoise = false;
            ambientSoundTime = -80;
            random.nextFloat();
            random.nextFloat();
            madeSound = true;
        }

        // is in sync
        if (madeSound) {
            ClientCommandHelper.sendFeedback("commands.cvillager.perfectlyInSync");
            return;
        }

        // is not in sync, needs to be re-synced
        VillagerRngSimulator copy = copy();
        int i = 0;
        while (!copy.madeSound) {
            i++;
            copy.simulateTick();
        }
        if (0 < i && i < 30) {
            // in this case, it's a believable jump that we're less than 30 ticks behind, so we'll advance by the amount we calculated to be what this tick should've been
            ClientCommandHelper.sendFeedback("commands.cvillager.tooManyTicksBehind", i);
            this.random = copy.random;
            this.ambientSoundTime = copy.ambientSoundTime;
            this.waitingTicks = copy.waitingTicks;
            this.madeSound = copy.madeSound;
        } else if (i > 30) {
            // in this case, it took so many ticks to advance to rsync, that it's safe to assume we are ahead of the server, so we'll let the server catch up by 30 ticks
            ClientCommandHelper.sendFeedback("commands.cvillager.tooManyTicksAhead");
            waitingTicks += 30;
        } else {
            ClientCommandHelper.sendFeedback("commands.cvillager.perfectlyInSync");
        }
    }
}
