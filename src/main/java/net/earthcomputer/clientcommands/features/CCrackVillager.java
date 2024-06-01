package net.earthcomputer.clientcommands.features;

import com.seedfinding.latticg.reversal.DynamicProgram;
import com.seedfinding.latticg.util.LCG;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CCrackVillager {

    public static WeakReference<Villager> targetVillager = null;

    static Consumer<Long> onCrackFinished;

    static List<Measurement> measurements = new ArrayList<>();
    static boolean cracked = false;
    static int validMeasures = 0;
    static boolean cracking = false;
    static int interval = 22;
    public static BlockPos clockPos;

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
        //if(validMeasures > 0)
        //    measurements.add(Measurement.skip(interval * 2)); // 2 random call every ticks

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
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.fail"), false);
                reset();
            }
            cracking = false;
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
}
