package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.ClientCommands;
import net.earthcomputer.clientcommands.features.EnchantmentDatabase;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.FileUtil;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientItemPredicateArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class EnchantmentDatabaseCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType INVALID_PATH_EXCEPTION = new SimpleCommandExceptionType(Component.literal("Invalid path"));
    private static final SimpleCommandExceptionType READ_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.literal("Read failed"));
    private static final SimpleCommandExceptionType WRITE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.literal("Write failed"));

    @SuppressWarnings("deprecation")
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("clientcommands.enchantmentDatabaseTools")) {
            dispatcher.register(literal("cenchantmentdatabase")
                .then(literal("create")
                    .executes(ctx -> createDatabase(
                        ctx.getSource(),
                        ctx.getSource().registryAccess().lookupOrThrow(Registries.ITEM).listElements().map(item -> item)))
                    .then(argument("item", clientItemPredicate(context))
                        .executes(ctx -> createDatabase(ctx.getSource(), getClientItemPredicate(ctx, "item").getPossibleItems().stream().map(Item::builtInRegistryHolder)))))
                .then(literal("merge")
                    .then(argument("fileA", string())
                        .then(argument("fileB", string())
                            .executes(ctx -> mergeDatabases(getString(ctx, "fileA"), getString(ctx, "fileB"))))))
                .then(literal("populatesubsets")
                    .then(argument("file", string())
                        .executes(ctx -> populateSubsets(getString(ctx, "file")))))
                .then(literal("printitems")
                    .executes(ctx -> printItems(ctx.getSource()))));
        }

    }

    private static int createDatabase(FabricClientCommandSource source, Stream<Holder<Item>> items) {
        EnchantmentDatabase database = new EnchantmentDatabase();
        database.addFromItems(source.registryAccess(), items);
        Path outputPath = ClientCommands.CONFIG_DIR.resolve("enchantment_database.nbt");
        try {
            NbtIo.writeCompressed(database.toNbt(), outputPath);
            LOGGER.info("Written enchantment database to {}", outputPath.toAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to write enchantments to {}", outputPath.toAbsolutePath(), e);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int mergeDatabases(String fileA, String fileB) throws CommandSyntaxException {
        try {
            FileUtil.validatePath(fileA);
        } catch (IllegalArgumentException e) {
            throw INVALID_PATH_EXCEPTION.create();
        }
        try {
            FileUtil.validatePath(fileB);
        } catch (IllegalArgumentException e) {
            throw INVALID_PATH_EXCEPTION.create();
        }

        EnchantmentDatabase databaseA, databaseB;
        try {
            CompoundTag nbtA = NbtIo.readCompressed(ClientCommands.CONFIG_DIR.resolve(fileA + ".nbt"), NbtAccounter.unlimitedHeap());
            databaseA = EnchantmentDatabase.fromNbt(nbtA);
            CompoundTag nbtB = NbtIo.readCompressed(ClientCommands.CONFIG_DIR.resolve(fileB + ".nbt"), NbtAccounter.unlimitedHeap());
            databaseB = EnchantmentDatabase.fromNbt(nbtB);
        } catch (IOException e) {
            throw READ_FAILED_EXCEPTION.create();
        }

        databaseA.merge(databaseB);

        Path mergedPath = ClientCommands.CONFIG_DIR.resolve("enchantment_database.nbt");
        try {
            NbtIo.writeCompressed(databaseA.toNbt(), mergedPath);
        } catch (IOException e) {
            throw WRITE_FAILED_EXCEPTION.create();
        }

        LOGGER.info("Written merged enchantments to {}", mergedPath.toAbsolutePath());
        return Command.SINGLE_SUCCESS;
    }

    private static int printItems(FabricClientCommandSource source) {
        List<List<Holder<Item>>> groupedItems = EnchantmentDatabase.groupItems(source.registryAccess());
        for (List<Holder<Item>> group : groupedItems) {
            String text = "- " + group.stream()
                .map(item -> item.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>"))
                .collect(Collectors.joining(", "));
            source.sendFeedback(Component.literal(text));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int populateSubsets(String file) throws CommandSyntaxException {
        try {
            FileUtil.validatePath(file);
        } catch (IllegalArgumentException e) {
            throw INVALID_PATH_EXCEPTION.create();
        }

        EnchantmentDatabase database;
        try {
            CompoundTag nbt = NbtIo.readCompressed(ClientCommands.CONFIG_DIR.resolve(file + ".nbt"), NbtAccounter.unlimitedHeap());
            database = EnchantmentDatabase.fromNbt(nbt);
        } catch (IOException e) {
            throw READ_FAILED_EXCEPTION.create();
        }

        database.populateSubsets();

        Path populatedPath = ClientCommands.CONFIG_DIR.resolve("enchantment_database.nbt");
        try {
            NbtIo.writeCompressed(database.toNbt(), populatedPath);
        } catch (IOException e) {
            throw WRITE_FAILED_EXCEPTION.create();
        }

        LOGGER.info("Written subset-populated enchantment database to {}", populatedPath.toAbsolutePath());
        return Command.SINGLE_SUCCESS;
    }
}
