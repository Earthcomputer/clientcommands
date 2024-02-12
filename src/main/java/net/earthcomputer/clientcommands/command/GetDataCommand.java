package net.earthcomputer.clientcommands.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.data.BlockDataAccessor;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.*;
import static dev.xpple.clientarguments.arguments.CEntityArgumentType.*;
import static dev.xpple.clientarguments.arguments.CNbtPathArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class GetDataCommand {

    private static final DynamicCommandExceptionType GET_UNKNOWN_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.data.get.unknown", arg));
    private static final SimpleCommandExceptionType GET_MULTIPLE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.data.get.multiple"));
    private static final SimpleCommandExceptionType INVALID_BLOCK_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.data.block.invalid"));

    public static final Function<String, AccessorType> CLIENT_ENTITY_DATA_ACCESSOR = argName -> new AccessorType() {
        @Override
        public DataAccessor getAccessor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
            return new EntityDataAccessor(getCEntity(ctx, argName));
        }

        @Override
        public ArgumentBuilder<FabricClientCommandSource, ?> addArgumentsToBuilder(ArgumentBuilder<FabricClientCommandSource, ?> builder, Function<ArgumentBuilder<FabricClientCommandSource, ?>, ArgumentBuilder<FabricClientCommandSource, ?>> subcommandAdder) {
            return builder.then(literal("entity").then(subcommandAdder.apply(argument(argName, entity()))));
        }
    };

    public static final Function<String, AccessorType> CLIENT_TILE_ENTITY_DATA_OBJECT = argName -> new AccessorType() {
        public DataAccessor getAccessor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
            BlockPos pos = getCBlockPos(ctx, argName + "Pos");
            BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(pos);
            if (blockEntity == null) {
                throw INVALID_BLOCK_EXCEPTION.create();
            } else {
                return new BlockDataAccessor(blockEntity, pos);
            }
        }

        public ArgumentBuilder<FabricClientCommandSource, ?> addArgumentsToBuilder(ArgumentBuilder<FabricClientCommandSource, ?> builder, Function<ArgumentBuilder<FabricClientCommandSource, ?>, ArgumentBuilder<FabricClientCommandSource, ?>> subcommandAdder) {
            return builder.then(literal("block").then(subcommandAdder.apply(argument(argName + "Pos", blockPos()))));
        }
    };

    public static List<Function<String, AccessorType>> OBJECT_TYPES = ImmutableList.of(CLIENT_ENTITY_DATA_ACCESSOR, CLIENT_TILE_ENTITY_DATA_OBJECT);
    public static List<AccessorType> TARGET_OBJECT_TYPES = OBJECT_TYPES.stream().map(it -> it.apply("target")).collect(ImmutableList.toImmutableList());

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        for (AccessorType objType : TARGET_OBJECT_TYPES) {
            //noinspection unchecked
            dispatcher.register((LiteralArgumentBuilder<FabricClientCommandSource>) objType.addArgumentsToBuilder(literal("cgetdata"), builder ->
                    builder.executes(ctx -> getData(ctx.getSource(), objType.getAccessor(ctx)))
                    .then(argument("path", nbtPath())
                        .executes(ctx -> getData(ctx.getSource(), objType.getAccessor(ctx), getCNbtPath(ctx, "path"))))));
        }
    }

    private static int getData(FabricClientCommandSource source, DataAccessor accessor) throws CommandSyntaxException {
        source.sendFeedback(accessor.getPrintSuccess(accessor.getData()));
        return Command.SINGLE_SUCCESS;
    }

    private static int getData(FabricClientCommandSource source, DataAccessor accessor, NbtPath path) throws CommandSyntaxException {
        Tag tag = getNbt(path, accessor);
        int ret;
        if (tag instanceof NumericTag) {
            ret = Mth.floor(((NumericTag) tag).getAsDouble());
        } else if (tag instanceof CollectionTag) {
            ret = ((CollectionTag<?>) tag).size();
        } else if (tag instanceof CompoundTag) {
            ret = ((CompoundTag) tag).size();
        } else if (tag instanceof StringTag) {
            ret = tag.getAsString().length();
        } else {
            throw GET_UNKNOWN_EXCEPTION.create(path.toString());
        }

        source.sendFeedback(accessor.getPrintSuccess(tag));
        return ret;
    }

    private static Tag getNbt(NbtPath path, DataAccessor accessor) throws CommandSyntaxException {
        Collection<Tag> tags = path.get(accessor.getData());
        Iterator<Tag> tagItr = tags.iterator();
        Tag firstTag = tagItr.next();
        if (tagItr.hasNext()) {
            throw GET_MULTIPLE_EXCEPTION.create();
        } else {
            return firstTag;
        }
    }

    private interface AccessorType {
        DataAccessor getAccessor(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException;
        ArgumentBuilder<FabricClientCommandSource, ?> addArgumentsToBuilder(ArgumentBuilder<FabricClientCommandSource, ?> builder, Function<ArgumentBuilder<FabricClientCommandSource, ?>, ArgumentBuilder<FabricClientCommandSource, ?>> subcommandAdder);
    }

}
