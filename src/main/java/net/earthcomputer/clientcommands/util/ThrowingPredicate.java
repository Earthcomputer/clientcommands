package net.earthcomputer.clientcommands.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

@FunctionalInterface
public interface ThrowingPredicate<T> {
    boolean test(T t) throws CommandSyntaxException;
}
