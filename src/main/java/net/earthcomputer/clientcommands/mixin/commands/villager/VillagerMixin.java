package net.earthcomputer.clientcommands.mixin.commands.villager;

import net.earthcomputer.clientcommands.command.VillagerCommand;
import net.earthcomputer.clientcommands.features.VillagerRngSimulator;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
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
    public VillagerRngSimulator clientcommands_getCrackedRandom() {
        return rng;
    }

    @Override
    public void clientcommands_onAmbientSoundPlayed() {
        rng.onAmbientSoundPlayed();
    }

//    public void clientcommands_onProfessionUpdate(VillagerProfession newProfession) {
//        Villager targetVillager = VillagerCracker.getVillager();
//        if (targetVillager instanceof IVillager iVillager && iVillager.clientcommands_getCrackedRandom().isCracked()) {
//
//            if (offers == null) {
//                return;
//            }
//            for (MerchantOffer offer : offers) {
//                if (offer.getItemCostB().isPresent()) {
//                    LOGGER.info("[x{}] {} + [x{}] {} = [x{}] {} ({})",
//                        offer.getItemCostA().count(),
//                        Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getItemCostA().item().value()).getPath()).getString(),
//                        offer.getItemCostB().get().count(),
//                        Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getItemCostB().get().item().value()).getPath()).getString(),
//                        offer.getResult().getCount(),
//                        I18n.get(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getPath()),
//                        offer.getResult().getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream().map(Component::getString).skip(1).collect(Collectors.joining(", ")));
//                } else {
//                    LOGGER.info("[x{}] {} = [x{}] {} ({})",
//                        offer.getItemCostA().count(),
//                        Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getItemCostA().item().value()).getPath()).getString(),
//                        offer.getResult().getCount(),
//                        Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getPath()).getString(),
//                        offer.getResult().getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream().map(Component::getString).skip(1).collect(Collectors.joining(", ")));
//                }
//            }
//        }
//    }

    @Override
    public void clientcommands_onServerTick() {
        rng.simulateTick();
    }

    @Override
    public int clientcommands_bruteForceOffers(VillagerTrades.ItemListing[] listings, VillagerProfession profession, int maxTicks, Predicate<VillagerCommand.Offer> predicate) {
        if (this instanceof IVillager iVillager && iVillager.clientcommands_getCrackedRandom().isCracked()) {
            VillagerProfession oldProfession = getVillagerData().getProfession();
            setVillagerData(getVillagerData().setProfession(profession));

            VillagerRngSimulator rng = this.rng.copy();
            int i = 0;
            while (i < maxTicks) {
                VillagerRngSimulator randomBranch = rng.copy();
                randomBranch.simulateBaseTick();
                if (randomBranch.anyOffersMatch(listings, (Villager) (Object) this, predicate)) {
                    setVillagerData(getVillagerData().setProfession(oldProfession));
                    return i;
                }
                randomBranch.simulateServerAiStep();
                rng.simulateTick();
                i++;
            }

            setVillagerData(getVillagerData().setProfession(oldProfession));
        }

        return -1;
    }
}
