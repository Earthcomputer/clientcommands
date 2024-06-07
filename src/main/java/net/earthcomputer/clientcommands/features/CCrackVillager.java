package net.earthcomputer.clientcommands.features;

import com.seedfinding.latticg.reversal.DynamicProgram;
import com.seedfinding.latticg.util.LCG;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class CCrackVillager {

    public static WeakReference<Villager> targetVillager = null;

    static Consumer<Long> onCrackFinished;

    static List<Measurement> measurements = new ArrayList<>();
    static boolean cracked = false;
    static int validMeasures = 0;
    static boolean cracking = false;
    static int interval = 0;
    public static BlockPos clockPos = null;

    public static List<Offer> goalOffers = new ArrayList<>();
    public static boolean findingOffers = false;



    public static void cancel() {
        targetVillager = null;
    }

    public static void setInterval(int i) {
        interval = i;
    }

    public static void onClockUpdate() {
        VillagerRNGSim.INSTANCE.onTick();
        if(!cracked && validMeasures > 0) {
            measurements.add(Measurement.skip(2));
        }
    }

    public static void onAmethyst(ClientboundSoundPacket packet) {
        Villager villager;
        if(targetVillager != null && (villager = targetVillager.get()) != null) {
            if(villager.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) < 1.0) {
                if(cracked) {
                    VillagerRNGSim.INSTANCE.onAmethyst(packet);
                } else {
                    new Thread(() -> crack(packet)).start();
                }
            }
        }
    }

    public static void onAmbient() {
        VillagerRNGSim.INSTANCE.onAmbient();
        if(!cracked && validMeasures > 0) {
            measurements.add(Measurement.skip(2));
        }
    }

    static void crack(ClientboundSoundPacket packet) {
        var lastChimeIntensity1_2 = (packet.getVolume() - 0.1f);
        var nextFloat = (packet.getPitch() - 0.5f) / lastChimeIntensity1_2;

        measurements.add(Measurement.nextFloat(nextFloat, 0.0015f));
        validMeasures++;

        if(validMeasures > 6 && !cracking) {
            cracking = true;
            var cachedMeasurements = new ArrayList<>(measurements);
            DynamicProgram program = DynamicProgram.create(new LCG(25214903917L, 11, 1L<<48));
            for(var measurement : cachedMeasurements)
                measurement.apply(program);
            var seeds = program.reverse().toArray();
            if(seeds.length == 1) {
                cracked = true;
                reset();
                VillagerRNGSim.INSTANCE.setSeed(seeds[0]);
                for(var measurement : cachedMeasurements) {
                    measurement.apply(VillagerRNGSim.INSTANCE.random);
                }
                onCrackFinished.accept(VillagerRNGSim.INSTANCE.getSeed());
            } else {
                var gui = Minecraft.getInstance().gui;
                if(clockPos == null) {
                    gui.getChat().addMessage(Component.translatable("commands.ccrackvillager.noClock"));
                }
                gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.fail"), false);
                reset();
            }
            cracking = false;
        } else {
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.progress", validMeasures), false);
        }
    }

    public static void crackVillager(LocalPlayer player, Consumer<Long> callback) {
        var world = player.getCommandSenderWorld();
        var villagers = world.getNearbyEntities(Villager.class, TargetingConditions.forNonCombat().selector(
                (villager -> villager.getBlockStateOn().is(Blocks.AMETHYST_BLOCK))
        ), player, player.getBoundingBox().deflate(30));
        if(!villagers.isEmpty()) {
            Villager target = null;
            double distance = 100;
            for(var villager : villagers) {
                var tmpDistance = villager.distanceToSqr(player);
                if(tmpDistance < distance) {
                    target = villager;
                    distance = tmpDistance;
                }
            }
            if(target != null) {
                targetVillager = new WeakReference<>(target);
                onCrackFinished = callback;
                reset();
                cracked = false;
                VillagerRNGSim.INSTANCE.lastAmbientCracked = false;
            }
        }
    }

    static void reset() {
        measurements.clear();
        validMeasures = 0;
    }

    public static class Offer implements Predicate<MerchantOffer> {

        Predicate<ItemStack> first = null;
        Predicate<ItemStack> second = null;
        Predicate<ItemStack> result = null;

        String firstDescription = "";
        String secondDescription = "";
        String resultDescription = "";
        String enchantmentDescription = "";

        public Offer withFirst(Predicate<ItemStack> predicate, String description) {
            first = predicate;
            firstDescription = description;
            return this;
        }
        public Offer withSecond(Predicate<ItemStack> predicate, String description) {
            second = predicate;
            secondDescription = description;
            return this;
        }
        public Offer withResult(Predicate<ItemStack> predicate, String description) {
            result = predicate;
            resultDescription = description;
            return this;
        }
        public Offer andEnchantment(Predicate<ItemStack> predicate, String description) {
            result = result.and(predicate);
            enchantmentDescription = description;
            return this;
        }

        @Override
        public boolean test(MerchantOffer offer) {
            if(first != null && !first.test(offer.getItemCostA().itemStack())) return false;
            if(second != null && !second.test(offer.getItemCostB().isPresent() ? offer.getItemCostB().get().itemStack() : null)) {
                return false;
            }
            return result == null || result.test(offer.getResult());
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(firstDescription);
            if(!secondDescription.isEmpty() && !result.isEmpty()) {
                result.append(" + ");
            }
            result.append(secondDescription);
            if(!resultDescription.isEmpty() && !result.isEmpty()) {
                result.append(" => ");
            }
            result.append(resultDescription);
            if(!enchantmentDescription.isEmpty()) {
                result.append(" with ").append(enchantmentDescription);
            }

            return result.toString();
        }
    }
}
