package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.VillagerCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
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
    private int callsAtStartOfBruteForce = 0;
    private int callsInBruteForce = 0;
    private int totalCalls = 0;
    private Villager parent;

    public VillagerRngSimulator(@Nullable LegacyRandomSource random, int ambientSoundTime, Villager parent) {
        this.random = random;
        this.ambientSoundTime = ambientSoundTime;
        this.parent = parent;
    }

    public VillagerRngSimulator copy() {
        VillagerRngSimulator that = new VillagerRngSimulator(random == null ? null : new LegacyRandomSource(random.seed.get() ^ 0x5deece66dL), ambientSoundTime, parent);
        that.waitingTicks = this.waitingTicks;
        that.madeSound = this.madeSound;
        that.firstAmbientNoise = this.firstAmbientNoise;
        that.callsAtStartOfBruteForce = this.callsAtStartOfBruteForce;
        that.callsInBruteForce = this.callsInBruteForce;
        that.totalCalls = this.totalCalls;
        return that;
    }

    public void simulateTick() {
        if (random == null) {
            return;
        }

        simulateBaseTick();
        simulateServerAiStep();

        if (callsInBruteForce > 0) {
            updateProgressBar();
        }
    }

    public boolean shouldInteractWithVillager() {
        boolean shouldInteractWithVillager = totalCalls - callsAtStartOfBruteForce >= callsInBruteForce && callsInBruteForce > 0;
        if (shouldInteractWithVillager) {
            reset();
        }
        return shouldInteractWithVillager;
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
        totalCalls += 1;
        if (random.nextInt(1000) < ambientSoundTime++ && !firstAmbientNoise) {
            random.nextFloat();
            random.nextFloat();
            totalCalls += 2;
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
        totalCalls += 1;
    }

    public void updateProgressBar() {
        ClientCommandHelper.updateOverlayProgressBar(Math.min(callsInBruteForce, totalCalls - callsAtStartOfBruteForce), callsInBruteForce, 50, 60);
    }

    @Nullable
    public VillagerCommand.Offer anyOffersMatch(VillagerTrades.ItemListing[] listings, Entity trader, Predicate<VillagerCommand.Offer> predicate) {
        if (!isCracked()) {
            return null;
        }

        ArrayList<VillagerTrades.ItemListing> newListings = new ArrayList<>(List.of(listings));
        int i = 0;
        while (i < 2 && !newListings.isEmpty()) {
            VillagerTrades.ItemListing listing = newListings.remove(random.nextInt(newListings.size()));
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer != null) {
                VillagerCommand.Offer x = new VillagerCommand.Offer(offer.getBaseCostA(), offer.getCostB(), offer.getResult());
                if (predicate.test(x)) {
                    return x;
                } else {
                    i++;
                }
            }
        }
        return null;
    }

    @Nullable
    public LegacyRandomSource getRandom() {
        return random;
    }

    public void setCallsUntilOpenGui(int calls) {
        callsAtStartOfBruteForce = totalCalls;
        callsInBruteForce = calls;
    }

    public int getTotalCalls() {
        return totalCalls;
    }

    public boolean isCracked() {
        return random != null && !firstAmbientNoise;
    }

    public void setRandom(@Nullable LegacyRandomSource random) {
        this.random = random;
    }

    public void reset() {
        random = null;
        firstAmbientNoise = true;
        totalCalls = 0;
        callsAtStartOfBruteForce = 0;
        callsInBruteForce = 0;
    }

    @Override
    public String toString() {
        return "VillagerRngSimulator[" +
            "seed=" + (random == null ? "null" : random.seed.get()) + ']';
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

        if (madeSound) {
            ClientCommandHelper.sendFeedback("commands.cvillager.inSync");
        } else {
            ClientCommandHelper.sendFeedback("commands.cvillager.outOfSync");
            reset();
        }
    }
}
