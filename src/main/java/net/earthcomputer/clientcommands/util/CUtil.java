package net.earthcomputer.clientcommands.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class CUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DynamicCommandExceptionType REGEX_TOO_SLOW_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.client.regexTooSlow", arg));

    private CUtil() {
    }

    public static boolean regexFindSafe(Pattern regex, CharSequence input) throws CommandSyntaxException {
        return regex.matcher(new FusedRegexInput(regex, input)).find();
    }

    @SuppressWarnings("NullableProblems")
    @NonNull
    public static RuntimeException sneakyThrow(Throwable e) {
        CUtil.sneakyThrowHelper(e);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrowHelper(Throwable e) throws T {
        throw (T) e;
    }

    public static <L, R> void forEither(Either<L, R> either, Consumer<? super L> left, Consumer<? super R> right) {
        either.<@Nullable Void>map(l -> {
            left.accept(l);
            return null;
        }, r -> {
            right.accept(r);
            return null;
        });
    }

    /** @noinspection OptionalIsPresent - no boxing! */
    public static int getEnchantmentLevel(RegistryAccess registryAccess, ResourceKey<Enchantment> enchantment, ItemStack stack) {
        Optional<Holder.Reference<Enchantment>> enchHolder = registryAccess.lookupOrThrow(Registries.ENCHANTMENT).get(enchantment);
        return enchHolder.isPresent() ? EnchantmentHelper.getItemEnchantmentLevel(enchHolder.get(), stack) : 0;
    }

    /** @noinspection OptionalIsPresent - no boxing! */
    public static int getEnchantmentLevel(ResourceKey<Enchantment> enchantment, LivingEntity entity) {
        Optional<Holder.Reference<Enchantment>> enchHolder = entity.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment);
        if (enchHolder.isEmpty()) {
            return 0;
        }
        return Arrays.stream(EquipmentSlot.values()).mapToInt(slot -> entity.getItemBySlot(slot).getEnchantments().getLevel(enchHolder.get())).max().orElse(0);
    }

    public static Optional<ItemStack> parseItemStack(HolderLookup.Provider registries, Tag nbt) {
        return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt)
            .resultOrPartial(error -> LOGGER.error("Tried to load invalid item: '{}'", error));
    }

    public static CompoundTag saveItemStack(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        }

        return (CompoundTag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    private static class FusedRegexInput implements CharSequence {
        private static final long FUSE_LENGTH = 50_000_000; // 50ms should be more than enough for a normal regex to do its matching

        private final long startTime;
        private final Pattern regex;
        private final CharSequence delegate;

        private FusedRegexInput(long startTime, Pattern regex, CharSequence delegate) {
            this.startTime = startTime;
            this.regex = regex;
            this.delegate = delegate;
        }

        // put the exception here to force the exception to be declared, the exception will be thrown by other methods via sneakyThrows
        @SuppressWarnings("RedundantThrows")
        private FusedRegexInput(Pattern regex, CharSequence delegate) throws CommandSyntaxException {
            this(System.nanoTime(), regex, delegate);
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int i) {
            checkFuse();
            return delegate.charAt(i);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new FusedRegexInput(startTime, regex, delegate.subSequence(start, end));
        }

        private void checkFuse() {
            if (System.nanoTime() - startTime > FUSE_LENGTH) {
                throw sneakyThrow(REGEX_TOO_SLOW_EXCEPTION.create(regex.pattern()));
            }
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
