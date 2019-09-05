package net.earthcomputer.clientcommands.command.arguments;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.block.pattern.CachedBlockPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.arguments.BlockPredicateArgumentType;
import net.minecraft.server.command.ServerCommandSource;

import java.util.function.Predicate;

public class ClientBlockPredicateArgumentType extends BlockPredicateArgumentType {

    private ClientBlockPredicateArgumentType() {}

    public static Predicate<CachedBlockPosition> getBlockPredicate(CommandContext<ServerCommandSource> context, String arg) throws CommandSyntaxException {
        //noinspection ConstantConditions
        return context.getArgument(arg, BlockPredicate.class).create(MinecraftClient.getInstance().getNetworkHandler().getTagManager());
    }

}
