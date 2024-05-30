package net.earthcomputer.clientcommands.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.datafixers.util.Either;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class CUtil {
    private static final DynamicCommandExceptionType REGEX_TOO_SLOW_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.client.regexTooSlow", arg));

    private CUtil() {
    }

    public static boolean regexFindSafe(Pattern regex, CharSequence input) throws CommandSyntaxException {
        return regex.matcher(new FusedRegexInput(regex, input)).find();
    }

    @NotNull
    public static RuntimeException sneakyThrow(Throwable e) {
        CUtil.sneakyThrowHelper(e);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrowHelper(Throwable e) throws T {
        throw (T) e;
    }

    public static <L, R> void forEither(Either<L, R> either, Consumer<? super L> left, Consumer<? super R> right) {
        either.<Void>map(l -> {
            left.accept(l);
            return null;
        }, r -> {
            right.accept(r);
            return null;
        });
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

        @NotNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return new FusedRegexInput(startTime, regex, delegate.subSequence(start, end));
        }

        private void checkFuse() {
            if (System.nanoTime() - startTime > FUSE_LENGTH) {
                throw sneakyThrow(REGEX_TOO_SLOW_EXCEPTION.create(regex.pattern()));
            }
        }

        @NotNull
        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
