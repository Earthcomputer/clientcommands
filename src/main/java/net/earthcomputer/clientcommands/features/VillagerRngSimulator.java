package net.earthcomputer.clientcommands.features;

import com.mojang.datafixers.util.Pair;
import com.seedfinding.latticg.math.component.BigFraction;
import com.seedfinding.latticg.math.component.BigMatrix;
import com.seedfinding.latticg.math.component.BigVector;
import com.seedfinding.latticg.math.lattice.enumerate.EnumerateRt;
import com.seedfinding.latticg.math.optimize.Optimize;
import com.seedfinding.mcseed.rand.JRand;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.VillagerCommand;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.LongStream;

public class VillagerRngSimulator {
    private static final BigMatrix[] LATTICES;
    private static final BigMatrix[] INVERSE_LATTICES;
    private static final BigVector[] OFFSETS;

    @Nullable
    private JRand random;
    private long prevRandomSeed;
    private int ambientSoundTime;
    private int prevAmbientSoundTime;
    private boolean madeSound = false;
    private int totalAmbientSounds = 0;
    private int callsAtStartOfBruteForce = 0;
    private int callsInBruteForce = 0;
    private int totalCalls = 0;
    private int prevTotalCalls;
    private float firstPitch = Float.NaN;
    private int ticksBetweenSounds = 0;
    private float secondPitch = Float.NaN;
    @Nullable
    private long[] seedsFromTwoPitches = null;
    @Nullable
    private ItemStack activeGoalResult = null;

    static {
        try {
            CompoundTag root = NbtIo.read(new DataInputStream(Objects.requireNonNull(VillagerRngSimulator.class.getResourceAsStream("/villager_lattice_data.nbt"))));
            ListTag lattices = root.getList("lattices", Tag.TAG_LONG_ARRAY);
            LATTICES = new BigMatrix[lattices.size()];
            ListTag lattice_inverses = root.getList("lattice_inverses", Tag.TAG_LONG_ARRAY);
            INVERSE_LATTICES = new BigMatrix[lattices.size()];
            ListTag offsets = root.getList("offsets", Tag.TAG_LONG_ARRAY);
            OFFSETS = new BigVector[offsets.size()];
            for (int i = 0; i < lattices.size(); i++) {
                long[] lattice = lattices.getLongArray(i);
                BigMatrix matrix = new BigMatrix(3, 3);
                matrix.set(0, 0, new BigFraction(lattice[0]));
                matrix.set(0, 1, new BigFraction(lattice[1]));
                matrix.set(0, 2, new BigFraction(lattice[2]));
                matrix.set(1, 0, new BigFraction(lattice[3]));
                matrix.set(1, 1, new BigFraction(lattice[4]));
                matrix.set(1, 2, new BigFraction(lattice[5]));
                matrix.set(2, 0, new BigFraction(lattice[6]));
                matrix.set(2, 1, new BigFraction(lattice[7]));
                matrix.set(2, 2, new BigFraction(lattice[8]));
                LATTICES[i] = matrix;
            }
            for (int i = 0; i < lattice_inverses.size(); i++) {
                long[] lattice_inverse = lattice_inverses.getLongArray(i);
                BigMatrix matrix = new BigMatrix(3, 3);
                matrix.set(0, 0, new BigFraction(lattice_inverse[0], 1L << 48));
                matrix.set(0, 1, new BigFraction(lattice_inverse[1], 1L << 48));
                matrix.set(0, 2, new BigFraction(lattice_inverse[2], 1L << 48));
                matrix.set(1, 0, new BigFraction(lattice_inverse[3], 1L << 48));
                matrix.set(1, 1, new BigFraction(lattice_inverse[4], 1L << 48));
                matrix.set(1, 2, new BigFraction(lattice_inverse[5], 1L << 48));
                matrix.set(2, 0, new BigFraction(lattice_inverse[6], 1L << 48));
                matrix.set(2, 1, new BigFraction(lattice_inverse[7], 1L << 48));
                matrix.set(2, 2, new BigFraction(lattice_inverse[8], 1L << 48));
                INVERSE_LATTICES[i] = matrix;
            }
            for (int i = 0; i < offsets.size(); i++) {
                long[] offset = offsets.getLongArray(i);
                OFFSETS[i] = new BigVector(0, offset[0], offset[1]);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public VillagerRngSimulator(@Nullable JRand random, int ambientSoundTime) {
        this.random = random;
        this.ambientSoundTime = ambientSoundTime;
    }

    public VillagerRngSimulator copy() {
        VillagerRngSimulator that = new VillagerRngSimulator(random == null ? null : random.copy(), ambientSoundTime);
        that.madeSound = this.madeSound;
        that.totalAmbientSounds = this.totalAmbientSounds;
        that.callsAtStartOfBruteForce = this.callsAtStartOfBruteForce;
        that.callsInBruteForce = this.callsInBruteForce;
        that.totalCalls = this.totalCalls;
        that.firstPitch = this.firstPitch;
        that.ticksBetweenSounds = this.ticksBetweenSounds;
        that.secondPitch = this.secondPitch;
        that.activeGoalResult = this.activeGoalResult;
        return that;
    }

    public void simulateTick() {
        // called on receiving clock packet at the beginning of the tick, simulates the rest of the tick

        if (random == null) {
            ambientSoundTime++;
            return;
        }

        prevRandomSeed = random.getSeed();
        prevAmbientSoundTime = ambientSoundTime;
        prevTotalCalls = totalCalls;

        simulateBaseTick();
        simulateServerAiStep();

        if (callsInBruteForce > 0) {
            updateProgressBar();
        }
    }

    private void revertSimulatedTick() {
        random.setSeed(prevRandomSeed);
        ambientSoundTime = prevAmbientSoundTime;
        totalCalls = prevTotalCalls;
    }

    public boolean shouldInteractWithVillager() {
        boolean shouldInteractWithVillager = totalCalls - callsAtStartOfBruteForce >= callsInBruteForce - Configs.villagerAdjustment * 2 && callsInBruteForce > 0;
        if (shouldInteractWithVillager) {
            reset();
        }
        return shouldInteractWithVillager;
    }

    private void simulateBaseTick() {
        // we have the server receiving ambient noise tell us if we have to do this to increment the random, this is so that our ambient sound time is synced up.
        totalCalls += 1;
        if (random.nextInt(1000) < ambientSoundTime++ && totalAmbientSounds > 0) {
            random.nextFloat();
            random.nextFloat();
            totalCalls += 2;
            ambientSoundTime = -80;
            madeSound = true;
        } else {
            madeSound = false;
        }
    }

    private void simulateServerAiStep() {
        random.nextInt(100);
        totalCalls += 1;
    }

    public void updateProgressBar() {
        ClientCommandHelper.updateOverlayProgressBar(Math.min(callsInBruteForce - Configs.villagerAdjustment * 2, totalCalls - callsAtStartOfBruteForce), callsInBruteForce - Configs.villagerAdjustment * 2, 50, 60);
    }

    @Nullable
    public VillagerCommand.Offer anyOffersMatch(VillagerTrades.ItemListing[] listings, Entity trader, Predicate<VillagerCommand.Offer> predicate) {
        if (!getCrackedState().isCracked()) {
            return null;
        }

        RandomSource rand = new LegacyRandomSource(random.getSeed() ^ 0x5deece66dL);;

        ArrayList<VillagerTrades.ItemListing> newListings = new ArrayList<>(List.of(listings));
        int i = 0;
        while (i < 2 && !newListings.isEmpty()) {
            VillagerTrades.ItemListing listing = newListings.remove(rand.nextInt(newListings.size()));
            MerchantOffer offer = listing.getOffer(trader, rand);
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

    public void setCallsUntilToggleGui(int calls, ItemStack resultStack) {
        callsAtStartOfBruteForce = totalCalls;
        callsInBruteForce = calls;
        activeGoalResult = resultStack;
    }

    public int getTotalCalls() {
        return totalCalls;
    }

    public CrackedState getCrackedState() {
        if (totalAmbientSounds == 0) {
            return CrackedState.PENDING_FIRST_AMBIENT_SOUND;
        }

        if (totalAmbientSounds == 1) {
            return CrackedState.PENDING_SECOND_AMBIENT_SOUND;
        }

        if (random == null) {
            return CrackedState.UNCRACKED;
        }

        return CrackedState.CRACKED;
    }

    public void setRandom(@Nullable JRand random) {
        this.random = random;
    }

    public void reset() {
        random = null;
        totalAmbientSounds = 0;
        totalCalls = 0;
        callsAtStartOfBruteForce = 0;
        callsInBruteForce = 0;
        firstPitch = Float.NaN;
        ticksBetweenSounds = 0;
        secondPitch = Float.NaN;
        seedsFromTwoPitches = null;
        activeGoalResult = null;
    }

    @Override
    public String toString() {
        return "VillagerRngSimulator[seed=" + (random == null ? "null" : random.getSeed()) + ']';
    }

    public void onAmbientSoundPlayed(float pitch) {
        if (totalAmbientSounds == 0) {
            totalAmbientSounds++;
            firstPitch = pitch;
            ambientSoundTime = -80;
            madeSound = true;
            ClientCommandHelper.addOverlayMessage(((MutableComponent) getCrackedState().getMessage(false)).withStyle(ChatFormatting.RED), 100);
            return;
        }

        if (totalAmbientSounds == 1) {
            totalAmbientSounds++;
            ticksBetweenSounds = ambientSoundTime - (-80);
            secondPitch = pitch;
            ambientSoundTime = -80;
            madeSound = true;

            if (seedsFromTwoPitches != null) {
                int matchingSeeds = 0;
                long matchingSeed = 0;
                nextSeed: for (long seed : seedsFromTwoPitches) {
                    JRand rand = JRand.ofInternalSeed(seed);
                    rand.nextInt(100);
                    for (int i = -80; i < ticksBetweenSounds - 80 - 1; i++) {
                        if (rand.nextInt(1000) < i) {
                            continue nextSeed;
                        }
                        rand.nextInt(100);
                    }
                    if (rand.nextInt(1000) >= ticksBetweenSounds - 80 - 1) {
                        continue;
                    }
                    float simulatedThirdPitch = (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1.0f;
                    if (simulatedThirdPitch == pitch) {
                        matchingSeeds++;
                        matchingSeed = rand.getSeed();
                    }
                }
                seedsFromTwoPitches = null;
                if (matchingSeeds == 1) {
                    random = JRand.ofInternalSeed(matchingSeed);
                    random.nextInt(100);
                    ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.crack.success", Long.toHexString(matchingSeed)).withStyle(ChatFormatting.GREEN), 100);
                    return;
                }
            }

            long[] seeds = crackSeed();
            if (seeds.length == 1) {
                random = JRand.ofInternalSeed(seeds[0]);
                random.nextInt(100);
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.crack.success", Long.toHexString(seeds[0])).withStyle(ChatFormatting.GREEN), 100);
            } else {
                totalAmbientSounds = 1;
                firstPitch = pitch;
                secondPitch = Float.NaN;
                seedsFromTwoPitches = seeds.length > 0 ? seeds : null;
                ambientSoundTime = -80;
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.crack.failed", seeds.length).withStyle(ChatFormatting.RED), 100);
            }
            return;
        }

        if (!madeSound) {
            ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.outOfSync").withStyle(ChatFormatting.RED), 100);
            reset();
        }
    }

    public void onNoSoundPlayed(float pitch, boolean fromGuiInteract) {
        // the last received action before the next tick's clock
        // played both when interacting with a villager without a profession and when using the villager gui

        if (random != null) {
            totalCalls += 2;
            if (fromGuiInteract) {
                ambientSoundTime = -80;
            }
            float simulatedPitch = (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f;
            if (pitch != simulatedPitch) {
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.outOfSync").withStyle(ChatFormatting.RED), 100);
                reset();
            }
        }
    }

    public void onYesSoundPlayed(float pitch) {
        // the last received action before the next tick's clock
        // played when using the villager gui

        if (random != null) {
            totalCalls += 2;
            ambientSoundTime = -80;
            float simulatedPitch = (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f;
            if (pitch != simulatedPitch) {
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.outOfSync").withStyle(ChatFormatting.RED), 100);
                reset();
            }
        }
    }

    public void onSplashSoundPlayed(float pitch) {
        // the first received action after this tick's clock

        if (random != null) {
            // simulateTick() was already called for this tick assuming no splash happened, so revert it and rerun it with the splash
            revertSimulatedTick();

            totalCalls += 2;
            float simulatedPitch = (random.nextFloat() - random.nextFloat()) * 0.4f + 1.0f;
            if (pitch != simulatedPitch) {
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.outOfSync").withStyle(ChatFormatting.RED), 100);
                reset();
                return;
            }

            int iterations = Mth.ceil(1.0f + EntityType.VILLAGER.getDimensions().width() * 20.0f);
            totalCalls += iterations * 10;
            random.advance(iterations * 10L);

            simulateTick();
        }
    }

    public void onXpOrbSpawned(int value) {
        // the last received action before the next tick's clock

        if (random != null) {
            totalCalls += 1;
            ambientSoundTime = -80;
            int simulatedValue = 3 + this.random.nextInt(4);
            boolean leveledUp = value > 3 + 3;
            if (leveledUp) simulatedValue += 5;
            if (value != simulatedValue) {
                ClientCommandHelper.addOverlayMessage(Component.translatable("commands.cvillager.outOfSync").withStyle(ChatFormatting.RED), 100);
                reset();
            }
        }
    }

    public Pair<Integer, VillagerCommand.Offer> bruteForceOffers(VillagerTrades.ItemListing[] listings, VillagerProfession profession, int minTicks, int maxCalls, Predicate<VillagerCommand.Offer> predicate) {
        Villager targetVillager = VillagerCracker.getVillager();
        if (targetVillager != null && getCrackedState().isCracked()) {
            VillagerProfession oldProfession = targetVillager.getVillagerData().getProfession();
            targetVillager.setVillagerData(targetVillager.getVillagerData().setProfession(profession));

            VillagerRngSimulator rng = this.copy();
            int startingCalls = rng.getTotalCalls();

            for (int i = 0; i < minTicks; i++) {
                rng.simulateTick();
            }

            while (rng.getTotalCalls() < maxCalls + startingCalls) {
                VillagerRngSimulator randomBranch = rng.copy();
                randomBranch.simulateTick();
                VillagerCommand.Offer offer = randomBranch.anyOffersMatch(listings, targetVillager, predicate);
                if (offer != null) {
                    targetVillager.setVillagerData(targetVillager.getVillagerData().setProfession(oldProfession));
                    // we do the calls before this ticks processing so that since with 0ms ping, the server reads it next tick
                    return Pair.of(rng.getTotalCalls() - startingCalls, offer);
                }
                rng.simulateTick();
            }

            targetVillager.setVillagerData(targetVillager.getVillagerData().setProfession(oldProfession));
        }

        return Pair.of(-1_000_000, null);
    }

    public long[] crackSeed() {
        if (!(80 <= ticksBetweenSounds && ticksBetweenSounds - 80 < LATTICES.length)) {
            return new long[0];
        }

        BigMatrix lattice = LATTICES[ticksBetweenSounds - 80];
        BigMatrix inverseLattice = INVERSE_LATTICES[ticksBetweenSounds - 80];
        BigVector offset = OFFSETS[ticksBetweenSounds - 80];

        float firstMin = Math.max(-1.0f + 0x1.0p-24f, (firstPitch - 1.0f) / 0.2f - VillagerCracker.MAX_ERROR);
        float firstMax = Math.min(1.0f - 0x1.0p-24f, (firstPitch - 1.0f) / 0.2f + VillagerCracker.MAX_ERROR);
        float secondMin = Math.max(-1.0f + 0x1.0p-24f, (secondPitch - 1.0f) / 0.2f - VillagerCracker.MAX_ERROR);
        float secondMax = Math.min(1.0f - 0x1.0p-24f, (secondPitch - 1.0f) / 0.2f + VillagerCracker.MAX_ERROR);

        firstMax = Math.nextUp(firstMax);
        secondMax = Math.nextUp(secondMax);

        long firstMinLong = (long) Math.ceil(firstMin * 0x1.0p24f);
        long firstMaxLong = (long) Math.ceil(firstMax * 0x1.0p24f) - 1;
        long secondMinLong = (long) Math.ceil(secondMin * 0x1.0p24f);
        long secondMaxLong = (long) Math.ceil(secondMax * 0x1.0p24f) - 1;

        long firstMinSeedDiff = (firstMinLong << 24) - 0xFFFFFF;
        long firstMaxSeedDiff = (firstMaxLong << 24) + 0xFFFFFF;
        long secondMinSeedDiff = (secondMinLong << 24) - 0xFFFFFF;
        long secondMaxSeedDiff = (secondMaxLong << 24) + 0xFFFFFF;

        long firstCombinationModMin = firstMinSeedDiff & 0xFFFFFFFFFFFFL;
        long firstCombinationModMax = firstMaxSeedDiff & 0xFFFFFFFFFFFFL;
        long secondCombinationModMin = secondMinSeedDiff & 0xFFFFFFFFFFFFL;
        long secondCombinationModMax = secondMaxSeedDiff & 0xFFFFFFFFFFFFL;

        firstCombinationModMax = firstCombinationModMax < firstCombinationModMin ? firstCombinationModMax + (1L << 48) : firstCombinationModMax;
        secondCombinationModMax = secondCombinationModMax < secondCombinationModMin ? secondCombinationModMax + (1L << 48) : secondCombinationModMax;

        Optimize optimize = Optimize.Builder.ofSize(3)
            .withLowerBound(0, 0)
            .withUpperBound(0, 0xFFFFFFFFFFFFL)
            .withLowerBound(1, firstCombinationModMin)
            .withUpperBound(1, firstCombinationModMax)
            .withLowerBound(2, secondCombinationModMin)
            .withUpperBound(2, secondCombinationModMax)
            .build();

        return EnumerateRt.enumerate(lattice, offset, optimize, inverseLattice, inverseLattice.multiply(offset)).mapToLong(vec -> vec.get(0).getNumerator().longValue() & ((1L << 48) - 1)).flatMap(seed -> {
            JRand rand = JRand.ofInternalSeed(seed);
            float simulatedFirstPitch = (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1.0f;
            rand.nextInt(100);
            for (int i = -80; i < ticksBetweenSounds - 80 - 1; i++) {
                if (rand.nextInt(1000) < i) {
                    return LongStream.empty();
                }
                rand.nextInt(100);
            }
            if (rand.nextInt(1000) >= ticksBetweenSounds - 80 - 1) {
                return LongStream.empty();
            }
            float simulatedSecondPitch = (rand.nextFloat() - rand.nextFloat()) * 0.2f + 1.0f;
            if (simulatedFirstPitch == firstPitch && simulatedSecondPitch == secondPitch) {
                return LongStream.of(rand.getSeed());
            } else {
                return LongStream.empty();
            }
        }).toArray();
    }

    public enum CrackedState {
        UNCRACKED,
        PENDING_FIRST_AMBIENT_SOUND,
        PENDING_SECOND_AMBIENT_SOUND,
        CRACKED;

        public boolean isCracked() {
            return this == CRACKED;
        }

        public Component getMessage(boolean addColor) {
            return switch (this) {
                case UNCRACKED -> Component.translatable("commands.cvillager.noCrackedVillagerPresent").withStyle(addColor ? ChatFormatting.RED : ChatFormatting.RESET);
                case PENDING_FIRST_AMBIENT_SOUND -> Component.translatable("commands.cvillager.inSync", 0).withStyle(addColor ? ChatFormatting.RED : ChatFormatting.RESET);
                case PENDING_SECOND_AMBIENT_SOUND -> Component.translatable("commands.cvillager.inSync", 50).withStyle(addColor ? ChatFormatting.RED : ChatFormatting.RESET);
                case CRACKED -> Component.translatable("commands.cvillager.inSync", 100).withStyle(addColor ? ChatFormatting.GREEN : ChatFormatting.RESET);
            };
        }
    }
}
