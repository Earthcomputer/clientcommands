package net.earthcomputer.clientcommands.features;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

public class VillagerRNGSim {
    public static LegacyRandomSource random = new LegacyRandomSource(0);

    static int errorCount = 0;
    static long lastAmbient = 0;
    static boolean lastAmbientCracked = false;

    public static void onAmethyst(ClientboundSoundPacket packet) {
        var lastChimeIntensity1_2 = (packet.getVolume() - 0.1f);
        var nextFloat = (packet.getPitch() - 0.5f) / lastChimeIntensity1_2;
        for(var i = 0; i < CCrackVillager.interval; i++) {
            random.nextFloat();
            random.nextFloat();
        }
        var maxRetry = 100;
        int i;
        for(i = 0; i < maxRetry; i++) {
            float predicted = random.nextFloat();
            if(Math.abs(nextFloat - predicted) < 0.0001) {
                break;
            }
            if(i == maxRetry-1) {
                errorCount++;
                if(errorCount > 6) {
                    errorCount = 0;
                    CCrackVillager.cracked = false;
                    Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.maintainFail"), false);
                }
                return;
            }
        }
        errorCount = 0;
        if(i < 2)
            Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.maintain"), false);
    }

    public static void onAmbient() {
        if(!lastAmbientCracked) {
            lastAmbientCracked = true;
            lastAmbient = -80;
        }
    }

    public static long getSeed() {
        return random.seed.get();
    }

    public static void setSeed(long seed) {
        random.setSeed(seed ^ 25214903917L);
        lastAmbientCracked = false;
    }
}