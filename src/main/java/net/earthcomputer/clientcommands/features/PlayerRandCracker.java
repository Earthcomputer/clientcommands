package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.multiconnect.api.MultiConnectAPI;
import net.earthcomputer.multiconnect.api.Protocols;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class PlayerRandCracker {

    // ===== RNG IMPLEMENTATION ===== //

    public static final long MULTIPLIER = 0x5deece66dL;
    public static final long ADDEND = 0xbL;
    public static final long MASK = (1L << 48) - 1;

    private static long seed;

    private static int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public static int nextInt() {
        return next(32);
    }

    public static int nextInt(int bound) {
        if ((bound & -bound) == bound)
            return (int) ((bound * (long)next(31)) >> 31);

        int bits, val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + (bound-1) < 0);

        return val;
    }

    public static float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public static void setSeed(long seed) {
        PlayerRandCracker.seed = seed;
    }

    public static long getSeed() {
        return seed;
    }


    // ===== RESET DETECTION + PLAYER RNG MAINTENANCE ===== //

    // TODO: update-sensitive: call hierarchy of PlayerEntity.random and PlayerEntity.getRandom()

    private static int expectedThrows = 0;

    public static void resetCracker() {
        TempRules.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
    }

    public static void resetCracker(String reason) {
        if (TempRules.playerCrackState != PlayerRandCracker.CrackState.UNCRACKED) {
            ClientCommandManager.sendFeedback(new LiteralText(Formatting.RED + I18n.translate(
                    "playerManip.reset", I18n.translate("playerManip.reset." + reason))));
        }
        resetCracker();
    }

    public static void onDropItem() {
        if (expectedThrows > 0 || canMaintainPlayerRNG())
            for (int i = 0; i < 4; i++)
                nextInt();
        else
            resetCracker("dropItem");
        if (expectedThrows > 0)
            expectedThrows--;
    }

    public static void onEntityCramming() {
        resetCracker("entityCramming");
    }

    public static void onDrink() {
        // TODO: was this ever a thing?
        //resetCracker("drink");
    }

    public static void onEat(ItemStack stack, Vec3d pos, int particleCount, int itemUseTimeLeft) {
        if (canMaintainPlayerRNG()) {
            //Every time a person eats, the particles are random, and when finished more particles spawn(16)
            for (int i = 0; i < particleCount * 3 + 3; i++) {
                nextInt();
            }

            if (TempRules.chorusManipulation && stack.getItem() == Items.CHORUS_FRUIT) {
                ChorusManipulation.onEat(pos, particleCount, itemUseTimeLeft);
                if (particleCount == 16) {
                    //Consumption randoms
                    for (int i = 0; i < 5; i++) {
                        nextInt();
                    }
                }
            }
        } else {
            resetCracker("food");
        }
    }

    public static void onUnderwater() {
        resetCracker("swim");
    }

    public static void onSwimmingStart() {
        resetCracker("enterWater");
    }

    public static void onDamage() {
        resetCracker("playerHurt");
    }

    public static void onSprinting() {
        resetCracker("sprint");
    }

    public static void onEquipmentBreak() {
        if (MultiConnectAPI.instance().getProtocolVersion() <= Protocols.V1_13_2) {
            resetCracker("itemBreak");
        }
    }

    public static void onPotionParticles() {
        resetCracker("potion");
    }

    public static void onGiveCommand() {
        resetCracker("give");
    }

    public static void onAnvilUse() {
        if (canMaintainPlayerRNG())
            nextInt();
        else
            resetCracker("anvil");
    }

    public static void onMending() {
        resetCracker("mending");
    }

    public static void onFrostWalker() {
        resetCracker("frostWalker");
    }

    public static void onSoulSpeed() {
        resetCracker("soulSpeed");
    }

    public static void onBaneOfArthropods() {
        if (canMaintainPlayerRNG())
            nextInt();
        else
            resetCracker("baneOfArthropods");
    }

    public static void onRecreatePlayer() {
        resetCracker("recreated");
    }

    public static void onUnbreaking(ItemStack stack, int amount, int unbreakingLevel) {
        if (canMaintainPlayerRNG())
            for (int i = 0; i < amount; i++)
                if (!(stack.getItem() instanceof ArmorItem) || nextFloat() >= 0.6)
                    nextInt(unbreakingLevel + 1);
                else
                    resetCracker("unbreaking");
    }

    public static void onUnbreakingUncertain(ItemStack stack, int minAmount, int maxAmount, int unbreakingLevel) {
        resetCracker("unbreaking");
    }

    // TODO: update-sensitive: call hierarchy of ItemStack.damage
    public static void onItemDamage(int amount, LivingEntity holder, ItemStack stack) {
        if (holder instanceof ClientPlayerEntity && !((ClientPlayerEntity) holder).abilities.creativeMode) {
            if (stack.isDamageable()) {
                if (amount > 0) {
                    int unbreakingLevel = EnchantmentHelper.getLevel(Enchantments.UNBREAKING, stack);
                    if (unbreakingLevel > 0)
                        onUnbreaking(stack, amount, unbreakingLevel);

                    if (TempRules.toolBreakWarning && stack.getDamage() + amount >= stack.getMaxDamage() - 30) {

                        if(stack.getDamage() + amount >= stack.getMaxDamage() - 15){
                            MinecraftClient.getInstance().player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 10,0.1f);
                        }

                        MutableText durability = new LiteralText(String.valueOf(stack.getMaxDamage() - stack.getDamage() - 1)).formatted(Formatting.RED);

                        MinecraftClient.getInstance().inGameHud.setOverlayMessage(
                                new TranslatableText("playerManip.toolBreakWarning", durability).formatted(Formatting.GOLD),
                                false);
                    }

                    if (TempRules.infiniteTools && TempRules.playerCrackState.knowsSeed()) {
                        throwItemsUntil(rand -> {
                            for (int i = 0; i < amount; i++) {
                                if (stack.getItem() instanceof ArmorItem && rand.nextFloat() < 0.6)
                                    return false;
                                if (rand.nextInt(unbreakingLevel + 1) == 0)
                                    return false;
                            }
                            return true;
                        }, 64);
                    }
                }
            }
        }
    }

    public static void onItemDamageUncertain(int minAmount, int maxAmount, LivingEntity holder, ItemStack stack) {
        if (holder instanceof ClientPlayerEntity && !((ClientPlayerEntity) holder).abilities.creativeMode) {
            if (stack.isDamageable()) {
                if (maxAmount > 0) {
                    int unbreakingLevel = EnchantmentHelper.getLevel(Enchantments.UNBREAKING, stack);
                    if (unbreakingLevel > 0)
                        onUnbreakingUncertain(stack, minAmount, maxAmount, unbreakingLevel);
                }
            }
        }
    }

    public static void onUnexpectedItemEnchant() {
        resetCracker("enchanted");
    }

    private static boolean canMaintainPlayerRNG() {
        if (TempRules.playerRNGMaintenance && TempRules.playerCrackState.knowsSeed()) {
            TempRules.playerCrackState = CrackState.CRACKED;
            return true;
        } else {
            return false;
        }
    }

    // ===== UTILITIES ===== //

    public static ThrowItemsResult throwItemsUntil(Predicate<Random> condition, int max) {
        if (!TempRules.playerCrackState.knowsSeed()) {
            return new ThrowItemsResult(ThrowItemsResult.Type.UNKNOWN_SEED);
        }
        TempRules.playerCrackState = CrackState.CRACKED;

        long seed = PlayerRandCracker.seed;
        Random rand = new Random(seed ^ MULTIPLIER);

        int itemsNeeded = 0;
        for (; itemsNeeded <= max && !condition.test(rand); itemsNeeded++) {
            for (int i = 0; i < 4; i++) {
                seed = (seed * MULTIPLIER + ADDEND) & MASK;
            }
            rand.setSeed(seed ^ MULTIPLIER);
        }
        if (itemsNeeded > max) {
            return new ThrowItemsResult(ThrowItemsResult.Type.NOT_POSSIBLE, itemsNeeded);
        }
        for (int i = 0; i < itemsNeeded; i++) {
            if (!throwItem()) {
                return new ThrowItemsResult(ThrowItemsResult.Type.NOT_ENOUGH_ITEMS, i, itemsNeeded);
            }
        }

        return new ThrowItemsResult(ThrowItemsResult.Type.SUCCESS);
    }

    public static boolean throwItem() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        Slot matchingSlot = getBestItemThrowSlot(player.currentScreenHandler.slots);
        if (matchingSlot == null) {
            return false;
        }
        expectedThrows++;
        MinecraftClient.getInstance().interactionManager.clickSlot(player.currentScreenHandler.syncId,
                matchingSlot.id, 0, SlotActionType.THROW, player);

        return true;
    }

    public static Slot getBestItemThrowSlot(List<Slot> slots) {
        Map<Item, Integer> itemCounts = new HashMap<>();
        for (Slot slot : slots) {
            if (slot.hasStack() && EnchantmentHelper.getLevel(Enchantments.BINDING_CURSE, slot.getStack()) == 0 && slot.getStack().getItem() != Items.CHORUS_FRUIT) {
                itemCounts.put(slot.getStack().getItem(), itemCounts.getOrDefault(slot.getStack().getItem(), 0) + slot.getStack().getCount());
            }
        }
        if (itemCounts.isEmpty())
            return null;
        //noinspection OptionalGetWithoutIsPresent
        Item preferredItem = itemCounts.keySet().stream().max(Comparator.comparingInt(Item::getMaxCount).thenComparing(itemCounts::get)).get();
        //noinspection OptionalGetWithoutIsPresent
        return slots.stream().filter(slot -> slot.getStack().getItem() == preferredItem).findFirst().get();
    }

    public static long singlePlayerCrackRNG() {
        ServerPlayerEntity serverPlayer = MinecraftClient.getInstance().getServer().getPlayerManager().getPlayer(MinecraftClient.getInstance().player.getUuid());
        long seed = getSeed(serverPlayer.getRandom());
        setSeed(seed);

        EnchantmentCracker.possibleXPSeeds.clear();
        EnchantmentCracker.possibleXPSeeds.add(serverPlayer.getEnchantmentTableSeed());

        TempRules.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        TempRules.enchCrackState = EnchantmentCracker.CrackState.CRACKED;
        return seed;
    }

    private static final Field RANDOM_SEED;
    static {
        try {
            RANDOM_SEED = Random.class.getDeclaredField("seed");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
        RANDOM_SEED.setAccessible(true);
    }
    public static long getSeed(Random rand) {
        try {
            return ((AtomicLong) RANDOM_SEED.get(rand)).get();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static class ThrowItemsResult {
        private final Type type;
        private final TranslatableText message;

        public ThrowItemsResult(Type type, Object... args) {
            this.type = type;
            this.message = new TranslatableText(type.getTranslationKey(), args);
        }

        public Type getType() {
            return type;
        }

        public TranslatableText getMessage() {
            return message;
        }

        public enum Type {
            NOT_ENOUGH_ITEMS(false, "playerManip.notEnoughItems"),
            NOT_POSSIBLE(false, "playerManip.throwError"),
            UNKNOWN_SEED(false, "commands.cenchant.uncracked"),
            SUCCESS(true, null),
            ;

            private final boolean success;
            private final String translationKey;

            Type(boolean success, String translationKey) {
                this.success = success;
                this.translationKey = translationKey;
            }

            public boolean isSuccess() {
                return success;
            }

            public String getTranslationKey() {
                return translationKey;
            }
        }
    }

    public static enum CrackState implements StringIdentifiable {
        UNCRACKED("uncracked"),
        CRACKED("cracked", true),
        ENCH_CRACKING_1("ench_cracking_1"),
        HALF_CRACKED("half_cracked"),
        ENCH_CRACKING_2("ench_cracking_2"),
        CRACKING("cracking"),
        EATING("eating"),
        MANIPULATING_ENCHANTMENTS("manipulating_enchantments"),
        WAITING_DUMMY_ENCHANT("waiting_dummy_enchant", true),
        ;

        private final String name;
        private final boolean knowsSeed;
        CrackState(String name) {
            this(name, false);
        }
        CrackState(String name, boolean knowsSeed) {
            this.name = name;
            this.knowsSeed = knowsSeed;
        }

        @Override
        public String asString() {
            return name;
        }

        public boolean knowsSeed() {
            return knowsSeed;
        }
    }

}
