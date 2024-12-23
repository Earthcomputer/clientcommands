package net.earthcomputer.clientcommands.command;

import com.google.common.hash.HashCode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.seedfinding.mccore.rand.seed.WorldSeed;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static com.mojang.brigadier.arguments.LongArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ServerSeedCommand {

    private static final SimpleCommandExceptionType IMPOSSIBLE_SEED_COMBINATION_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cserverseed.hashedSeed.impossibleSeedCombination"));
    private static final SimpleCommandExceptionType ENCHANTMENT_SEED_NOT_SENT_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cserverseed.enchantmentSeedNotSent"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cserverseed")
            .then(literal("enchantmentSeed")
                .executes(ctx -> enchantmentSeed(ctx.getSource())))
            .then(literal("hashedSeed")
                .executes(ctx -> hashedSeed(ctx.getSource()))
                .then(argument("structureSeed", longArg())
                    .executes(ctx -> fromStructureSeed(ctx.getSource(), getLong(ctx, "structureSeed"))))));
    }

    private static int enchantmentSeed(FabricClientCommandSource source) throws CommandSyntaxException {
        int seed = source.getPlayer().getEnchantmentSeed();
        if (seed == 0) {
            throw ENCHANTMENT_SEED_NOT_SENT_EXCEPTION.create();
        }
        // The enchantment seed is a Java int, which is 32 bits.
        // However, the server sends the enchantment seed as short to the client.
        // This causes the 16 higher bits to be ignored, leaving only the 16 lower bits.
        seed &= 0x0000ffff;
        source.sendFeedback(Component.translatable("commands.cserverseed.enchantmentSeed", ComponentUtils.copyOnClickText(StringUtils.leftPad(Integer.toBinaryString(seed), 16, '0'))));
        return seed;
    }

    private static int hashedSeed(FabricClientCommandSource source) {
        long hashedSeed = source.getWorld().getBiomeManager().biomeZoomSeed;
        HashCode seedHash = HashCode.fromLong(hashedSeed);
        source.sendFeedback(Component.translatable("commands.cserverseed.hashedSeed", ComponentUtils.copyOnClickText(seedHash.toString())));
        return (int) hashedSeed;
    }

    private static int fromStructureSeed(FabricClientCommandSource source, long structureSeed) throws CommandSyntaxException {
        long hashedSeed = source.getWorld().getBiomeManager().biomeZoomSeed;
        List<Long> seeds = WorldSeed.fromHash(structureSeed, hashedSeed);
        if (seeds.isEmpty()) {
            throw IMPOSSIBLE_SEED_COMBINATION_EXCEPTION.create();
        }
        // Collision is extremely unlikely, so just call `getFirst`
        long worldSeed = seeds.getFirst();
        source.sendFeedback(Component.translatable("commands.cserverseed.hashedSeed.fromStructureSeed", ComponentUtils.copyOnClickText(String.valueOf(worldSeed))));
        return (int) worldSeed;
    }
}
