package net.earthcomputer.clientcommands;

import dev.xpple.betterconfig.api.Config;
import net.earthcomputer.clientcommands.features.ChorusManipulation;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.FishingCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

public class Configs {

    @Config(readOnly = true, temporary = true)
    public static double calcAnswer = 0;

    @Config(readOnly = true, temporary = true)
    public static EnchantmentCracker.CrackState enchCrackState = EnchantmentCracker.CrackState.UNCRACKED;

    @Config(readOnly = true, temporary = true)
    public static PlayerRandCracker.CrackState playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;

    @Config(setter = @Config.Setter("setEnchantingPrediction"), temporary = true)
    private static boolean enchantingPrediction = false;
    public static boolean getEnchantingPrediction() {
        return enchantingPrediction;
    }
    public static void setEnchantingPrediction(boolean enchantingPrediction) {
        Configs.enchantingPrediction = enchantingPrediction;
        if (enchantingPrediction) {
            ServerBrandManager.rngWarning();
        } else {
            EnchantmentCracker.resetCracker();
        }
    }

    public enum FishingManipulation implements StringIdentifiable {
        OFF,
        MANUAL,
        AFK;

        @Override
        public String asString() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public boolean isEnabled() {
            return this != OFF;
        }
    }

    @Config(setter = @Config.Setter("setFishingManipulation"), temporary = true)
    private static FishingManipulation fishingManipulation = FishingManipulation.OFF;
    public static FishingManipulation getFishingManipulation() {
        return fishingManipulation;
    }
    public static void setFishingManipulation(FishingManipulation fishingManipulation) {
        Configs.fishingManipulation = fishingManipulation;
        if (fishingManipulation.isEnabled()) {
            ServerBrandManager.rngWarning();
        } else {
            FishingCracker.reset();
        }
    }

    @Config(temporary = true)
    public static boolean playerRNGMaintenance = true;

    @Config
    public static boolean toolBreakWarning = false;

    @Config(setter = @Config.Setter("setMaxEnchantItemThrows"))
    private static int maxEnchantItemThrows = 64 * 32;
    public static int getMaxEnchantItemThrows() {
        return maxEnchantItemThrows;
    }
    public static void setMaxEnchantItemThrows(int maxEnchantItemThrows) {
        Configs.maxEnchantItemThrows = MathHelper.clamp(maxEnchantItemThrows, 0, 1000000);
    }

    @Config(setter = @Config.Setter("setChorusManipulation"), temporary = true)
    private static boolean chorusManipulation = false;
    public static boolean getChorusManipulation() {
        return chorusManipulation;
    }
    public static void setChorusManipulation(boolean chorusManipulation) {
        Configs.chorusManipulation = chorusManipulation;
        if (chorusManipulation) {
            ServerBrandManager.rngWarning();
            ChorusManipulation.onChorusManipEnabled();
        }
    }

    @Config(setter = @Config.Setter("setMaxChorusItemThrows"))
    private static int maxChorusItemThrows = 64 * 32;
    public static int getMaxChorusItemThrows() {
        return maxChorusItemThrows;
    }
    public static void setMaxChorusItemThrows(int maxChorusItemThrows) {
        Configs.maxChorusItemThrows = MathHelper.clamp(maxChorusItemThrows, 0, 1000000);
    }

    @Config(temporary = true)
    public static boolean infiniteTools = false;

    @Config
    public static int commandExecutionLimit = 65536;

    @Config
    public static boolean acceptC2CPackets = false;
}
