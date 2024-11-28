package net.earthcomputer.clientcommands.test;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.util.List;
import java.util.stream.Collectors;

public final class EntityRandomCallHierarchyTest {
    // TODO: regression test for enchantment value effects actually using the random

    @Test
    public void testPlayer() {
        TestUtil.regressionTest("playerRandomHierarchy", out -> {
            CallHierarchyWalker.fromField("net/minecraft/world/entity/Entity", "random", "Lnet/minecraft/util/RandomSource;")
                .runtimeOwnerType("net/minecraft/server/level/ServerPlayer")
                .recurseThrough("net/minecraft/world/entity/Entity", "getRandom", "()Lnet/minecraft/util/RandomSource;")
                .recurseThrough("net/minecraft/world/item/enchantment/EnchantmentHelper", "getRandomItemWith", "(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/Optional;")
                .recurseThrough("net/minecraft/world/item/enchantment/Enchantment", "modifyEntityFilteredValue", "(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lorg/apache/commons/lang3/mutable/MutableFloat;)V")
                .recurseThrough("net/minecraft/world/entity/Entity", "getRandomX", "(D)D")
                .recurseThrough("net/minecraft/world/entity/Entity", "getRandomY", "()D")
                .recurseThrough("net/minecraft/world/entity/Entity", "getRandomZ", "(D)D")
                .recurseThrough("net/minecraft/server/level/ServerPlayer", "playNotifySound", "(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V")
                .recurseThrough("net/minecraft/world/entity/LivingEntity", "spawnItemParticles", "(Lnet/minecraft/world/item/ItemStack;I)V")
                .walk((reference, callStack) -> {
                    if (!reference.owner().startsWith("net/minecraft/world/entity/ai/")) {
                        printReference(out, reference, callStack);
                    }
                });
        });
    }

    @Test
    public void testTeleportRandomly() {
        TestUtil.regressionTest("teleportRandomlyHierarchy", out -> {
            CallHierarchyWalker.fromMethod("net/minecraft/world/item/consume_effects/TeleportRandomlyConsumeEffect", "<init>", "(F)V")
                .recurseThrough("net/minecraft/world/item/consume_effects/TeleportRandomlyConsumeEffect", "<init>", "()V")
                .walk((reference, callStack) -> printReference(out, reference, callStack));
        });
    }

    @Test
    public void testEnchantmentRemoveBinomial() {
        TestUtil.regressionTest("testEnchantmentRemoveBinomialHierarchy", out -> {
            CallHierarchyWalker.fromMethod("net/minecraft/world/item/enchantment/effects/RemoveBinomial", "<init>", "(Lnet/minecraft/world/item/enchantment/LevelBasedValue;)V")
                .walk((reference, callStack) -> printReference(out, reference, callStack));
        });
    }

    @Test
    public void testEnchantmentPlaySoundEffect() {
        TestUtil.regressionTest("enchantmentPlaySoundEffectHierarchy", out -> {
            CallHierarchyWalker.fromMethod("net/minecraft/world/item/enchantment/effects/PlaySoundEffect", "<init>", "(Lnet/minecraft/core/Holder;Lnet/minecraft/util/valueproviders/FloatProvider;Lnet/minecraft/util/valueproviders/FloatProvider;)V")
                .walk((reference, callStack) -> printReference(out, reference, callStack));
        });
    }

    @Test
    public void testEnchantmentReplaceBlockEffect() {
        TestUtil.regressionTest("enchantmentReplaceBlockEffectHierarchy", out -> {
            CallHierarchyWalker.fromMethod("net/minecraft/world/item/enchantment/effects/ReplaceBlock", "<init>", "(Lnet/minecraft/core/Vec3i;Ljava/util/Optional;Lnet/minecraft/world/level/levelgen/feature/stateproviders/BlockStateProvider;Ljava/util/Optional;)V")
                .walk((reference, callStack) -> printReference(out, reference, callStack));
        });
    }

    @Test
    public void testEnchantmentReplaceDiskEffect() {
        TestUtil.regressionTest("enchantmentReplaceDiskEffectHierarchy", out -> {
            CallHierarchyWalker.fromMethod("net/minecraft/world/item/enchantment/effects/ReplaceDisk", "<init>", "(Lnet/minecraft/world/item/enchantment/LevelBasedValue;Lnet/minecraft/world/item/enchantment/LevelBasedValue;Lnet/minecraft/core/Vec3i;Ljava/util/Optional;Lnet/minecraft/world/level/levelgen/feature/stateproviders/BlockStateProvider;Ljava/util/Optional;)V")
                .walk((reference, callStack) -> printReference(out, reference, callStack));
        });
    }

    @Test
    public void testEnchantmentSpawnParticlesEffect() {
        TestUtil.regressionTest("enchantmentSpawnParticlesEffectHierarchy", out -> {
            CallHierarchyWalker.fromMethod("net/minecraft/world/item/enchantment/effects/SpawnParticlesEffect", "<init>", "(Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/world/item/enchantment/effects/SpawnParticlesEffect$PositionSource;Lnet/minecraft/world/item/enchantment/effects/SpawnParticlesEffect$PositionSource;Lnet/minecraft/world/item/enchantment/effects/SpawnParticlesEffect$VelocitySource;Lnet/minecraft/world/item/enchantment/effects/SpawnParticlesEffect$VelocitySource;Lnet/minecraft/util/valueproviders/FloatProvider;)V")
                .walk((reference, callStack) -> printReference(out, reference, callStack));
        });
    }

    private void printReference(PrintWriter out, ReferencesFinder.OwnerNameAndDesc reference, List<ReferencesFinder.OwnerNameAndDesc> callStack) {
        out.printf("%s.%s %s <- %s%n", reference.owner(), reference.name(), reference.desc(), callStack.reversed().stream().map(method -> method.owner() + "." + method.name()).collect(Collectors.joining(" <- ")));
    }
}
