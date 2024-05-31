package net.earthcomputer.clientcommands.features;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class VillagerRngSimulator {
    private static final Logger LOGGER = LogUtils.getLogger();

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

    @Override
    public VillagerRngSimulator clone() {
        VillagerRngSimulator that = new VillagerRngSimulator(random == null ? null : new LegacyRandomSource(random.seed.get() ^ 0x5deece66dL), ambientSoundTime);
        that.waitingTicks = this.waitingTicks;
        that.madeSound = this.madeSound;
        that.firstAmbientNoise = this.firstAmbientNoise;
        return that;
    }

    public void simulateTick() {
        if (waitingTicks > 0) {
            waitingTicks--;
            return;
        }

        LOGGER.info("Client (pre-tick): {}", this);

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

        random.nextInt(100);
    }

    @Nullable
    public MerchantOffers simulateTrades(Villager villager) {
        VillagerData villagerData = villager.getVillagerData();
        Int2ObjectMap<VillagerTrades.ItemListing[]> map = VillagerTrades.TRADES.get(villagerData.getProfession());

        if (map == null || map.isEmpty()) {
            return null;
        }

        return simulateOffers(map.get(villagerData.getLevel()), villager);
    }

    private MerchantOffers simulateOffers(VillagerTrades.ItemListing[] listings, Entity trader) {
        if (random == null) {
            return null;
        }

        MerchantOffers offers = new MerchantOffers();
        ArrayList<VillagerTrades.ItemListing> newListings = new ArrayList<>(List.of(listings));
        int i = 0;
        while (i < 2 && !newListings.isEmpty()) {
            VillagerTrades.ItemListing listing = newListings.remove(random.nextInt(newListings.size()));
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer != null) {
                offers.add(offer);
                i++;
            }
        }
        return offers;
    }

    @Nullable
    public LegacyRandomSource getRandom() {
        return random;
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
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("commands.cvillager.perfectlyInSync"));
            return;
        }

        // is not in sync, needs to be re-synced
        VillagerRngSimulator clone = clone();
        int i = 0;
        while (!clone.madeSound) {
            i++;
            clone.simulateTick();
        }
        // todo, use ping if it's meant to be used (the idea here is to sync up to when the server says the villager makes a noise)
        if (0 < i && i < 30) {
            // in this case, it's a believable jump that we're less than 30 ticks behind, so we'll advance by the amount we calculated to be what this tick should've been
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("commands.cvillager.tooManyTicksBehind", i));
            this.random = clone.random;
            this.ambientSoundTime = clone.ambientSoundTime;
            this.waitingTicks = clone.waitingTicks;
            this.madeSound = clone.madeSound;
        } else if (i > 30) {
            // in this case, it took so many ticks to advance to rsync, that it's safe to assume we are ahead of the server, so we'll let the server catch up by 30 ticks
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("commands.cvillager.tooManyTicksAhead"));
            waitingTicks += 30;
        } else {
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("commands.cvillager.perfectlyInSync"));
        }
    }
}
