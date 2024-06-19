package net.earthcomputer.clientcommands.mixin.commands.villager;

import com.mojang.datafixers.util.Pair;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.VillagerCommand;
import net.earthcomputer.clientcommands.features.VillagerRngSimulator;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.Predicate;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements IVillager {
    @Shadow public abstract VillagerData getVillagerData();

    @Shadow public abstract void setVillagerData(VillagerData data);

    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    VillagerRngSimulator rng = new VillagerRngSimulator(null, -80);

    @Override
    public void clientcommands_setCrackedRandom(@Nullable RandomSource random) {
        rng.setRandom((LegacyRandomSource) random);
    }

    @Override
    public void clientcommands_onAmbientSoundPlayed(float pitch) {
        rng.onAmbientSoundPlayed(pitch);
    }

    @Override
    public void clientcommands_onServerTick() {
        rng.simulateTick();

        if (rng.shouldInteractWithVillager()) {
            Minecraft minecraft = Minecraft.getInstance();
            InteractionResult result = minecraft.gameMode.interact(minecraft.player, this, InteractionHand.MAIN_HAND);
            if (result.consumesAction() && result.shouldSwing()) {
                minecraft.player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    @Override
    public Pair<Integer, VillagerCommand.Offer> clientcommands_bruteForceOffers(VillagerTrades.ItemListing[] listings, VillagerProfession profession, int maxCalls, Predicate<VillagerCommand.Offer> predicate) {
        if (this instanceof IVillager iVillager && iVillager.clientcommands_getCrackedRandom().getCrackedState().isCracked()) {
            VillagerProfession oldProfession = getVillagerData().getProfession();
            setVillagerData(getVillagerData().setProfession(profession));

            VillagerRngSimulator rng = this.rng.copy();
            int startingCalls = rng.getTotalCalls();
            while (rng.getTotalCalls() < maxCalls + startingCalls) {
                VillagerRngSimulator randomBranch = rng.copy();
                randomBranch.simulateBaseTick();
                randomBranch.simulateServerAiStep();
                VillagerCommand.Offer offer = randomBranch.anyOffersMatch(listings, (Villager) (Object) this, predicate);
                if (offer != null) {
                    setVillagerData(getVillagerData().setProfession(oldProfession));
                    // we do the calls before this ticks processing so that since with 0ms ping, the server reads it next tick
                    return Pair.of(rng.getTotalCalls() - startingCalls, offer);
                }
                rng.simulateTick();
            }

            setVillagerData(getVillagerData().setProfession(oldProfession));
        }

        return Pair.of(-1_000_000, null);
    }

    @Override
    public VillagerRngSimulator clientcommands_getCrackedRandom() {
        return rng;
    }
}
