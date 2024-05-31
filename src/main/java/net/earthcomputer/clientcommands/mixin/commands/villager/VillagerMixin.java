package net.earthcomputer.clientcommands.mixin.commands.villager;

import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.features.VillagerCracker;
import net.earthcomputer.clientcommands.features.VillagerRngSimulator;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.stream.Collectors;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements IVillager {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    VillagerRngSimulator rng = new VillagerRngSimulator(null, -80);

    @Override
    public void clientcommands_setCrackedRandom(RandomSource random) {
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

    @Override
    public void clientcommands_onServerTick() {
        rng.simulateTick();
    }

    @Inject(method = "updateTrades", at = @At("HEAD"))
    public void onUpdateTrades(CallbackInfo ci) {
        if (!level().isClientSide) {
            LOGGER.info("Server Seed (b4 trade): {}", ((LegacyRandomSource) random).seed.get());

            Villager targetVillager = VillagerCracker.getVillager();
            if (targetVillager != null && this.getUUID().equals(targetVillager.getUUID()) && ((IVillager) targetVillager).clientcommands_getCrackedRandom() != null) {
                VillagerRngSimulator randomBranch = ((IVillager) targetVillager).clientcommands_getCrackedRandom().clone();
                LOGGER.info("Client Seed (pre-interact): {}", randomBranch);
                randomBranch.simulateTick();
                LOGGER.info("Client Seed (post-tick): {}", randomBranch);
                targetVillager.setVillagerData(targetVillager.getVillagerData().setProfession(VillagerProfession.LIBRARIAN));
                MerchantOffers offers = randomBranch.simulateTrades(targetVillager);
                targetVillager.setVillagerData(targetVillager.getVillagerData().setProfession(VillagerProfession.NONE));
                if (offers == null) {
                    return;
                }
                for (MerchantOffer offer : offers) {
                    if (offer.getItemCostB().isPresent()) {
                        LOGGER.info("[x{}] {} + [x{}] {} = [x{}] {} ({})",
                            offer.getItemCostA().count(),
                            Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getItemCostA().item().value()).getPath()).getString(),
                            offer.getItemCostB().get().count(),
                            Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getItemCostB().get().item().value()).getPath()).getString(),
                            offer.getResult().getCount(),
                            I18n.get(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getPath()),
                            offer.getResult().getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream().map(Component::getString).skip(1).collect(Collectors.joining(", ")));
                    } else {
                        LOGGER.info("[x{}] {} = [x{}] {} ({})",
                            offer.getItemCostA().count(),
                            Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getItemCostA().item().value()).getPath()).getString(),
                            offer.getResult().getCount(),
                            Component.translatable(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getPath()).getString(),
                            offer.getResult().getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).stream().map(Component::getString).skip(1).collect(Collectors.joining(", ")));
                    }
                }
                LOGGER.info("Client Seed (post-interact): {}", randomBranch);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void startTick(CallbackInfo ci) {
        if (!level().isClientSide) {
            LOGGER.info("Server Seed (pre-tick): {}", ((LegacyRandomSource) random).seed.get());
        }
    }
}
