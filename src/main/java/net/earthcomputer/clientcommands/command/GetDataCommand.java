package net.earthcomputer.clientcommands.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.BlockDataObject;
import net.minecraft.command.DataCommandObject;
import net.minecraft.command.EntityDataObject;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.nbt.*;
import net.minecraft.server.command.DataCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.*;
import static net.minecraft.command.argument.BlockPosArgumentType.*;
import static net.minecraft.command.argument.NbtPathArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class GetDataCommand {

    private static final DynamicCommandExceptionType GET_UNKNOWN_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.data.get.unknown", arg));
    private static final SimpleCommandExceptionType GET_MULTIPLE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.data.get.multiple"));
    private static final SimpleCommandExceptionType INVALID_BLOCK_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.data.block.invalid"));

    public static final Function<String, DataCommand.ObjectType> CLIENT_ENTITY_DATA_OBJECT = argName -> new DataCommand.ObjectType() {
        @Override
        public DataCommandObject getObject(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
            return new EntityDataObject(getEntity(ctx, argName));
        }

        @Override
        public ArgumentBuilder<ServerCommandSource, ?> addArgumentsToBuilder(ArgumentBuilder<ServerCommandSource, ?> builder, Function<ArgumentBuilder<ServerCommandSource, ?>, ArgumentBuilder<ServerCommandSource, ?>> subcommandAdder) {
            return builder.then(literal("entity").then(subcommandAdder.apply(argument(argName, entity()))));
        }
    };

    public static final Function<String, DataCommand.ObjectType> CLIENT_TILE_ENTITY_DATA_OBJECT = argName -> new DataCommand.ObjectType() {
        public DataCommandObject getObject(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
            BlockPos pos = getBlockPos(ctx, argName + "Pos");
            BlockEntity blockEntity = MinecraftClient.getInstance().world.getBlockEntity(pos);
            if (blockEntity == null) {
                throw INVALID_BLOCK_EXCEPTION.create();
            } else {
                return new BlockDataObject(blockEntity, pos);
            }
        }

        public ArgumentBuilder<ServerCommandSource, ?> addArgumentsToBuilder(ArgumentBuilder<ServerCommandSource, ?> builder, Function<ArgumentBuilder<ServerCommandSource, ?>, ArgumentBuilder<ServerCommandSource, ?>> subcommandAdder) {
            return builder.then(literal("block").then(subcommandAdder.apply(argument(argName + "Pos", blockPos()))));
        }
    };

    public static List<Function<String, DataCommand.ObjectType>> OBJECT_TYPES = ImmutableList.of(CLIENT_ENTITY_DATA_OBJECT, CLIENT_TILE_ENTITY_DATA_OBJECT);
    @SuppressWarnings("UnstableApiUsage")
    public static List<DataCommand.ObjectType> TARGET_OBJECT_TYPES = OBJECT_TYPES.stream().map(it -> it.apply("target")).collect(ImmutableList.toImmutableList());

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cgetdata");

        for (DataCommand.ObjectType objType : TARGET_OBJECT_TYPES) {
            //noinspection unchecked
            dispatcher.register((LiteralArgumentBuilder<ServerCommandSource>) objType.addArgumentsToBuilder(literal("cgetdata"), builder ->
                    builder.executes(ctx -> getData(ctx.getSource(), objType.getObject(ctx)))
                    .then(argument("path", nbtPath())
                        .executes(ctx -> getData(ctx.getSource(), objType.getObject(ctx), getNbtPath(ctx, "path"))))));
        }
    }

    private static int getData(ServerCommandSource source, DataCommandObject dataObj) throws CommandSyntaxException {
        sendFeedback(dataObj.feedbackQuery(dataObj.getNbt()));
        return 1;
    }

    private static int getData(ServerCommandSource source, DataCommandObject dataObj, NbtPathArgumentType.NbtPath path) throws CommandSyntaxException {
        NbtElement tag = getNbt(path, dataObj);
        int ret;
        if (tag instanceof AbstractNbtNumber) {
            ret = MathHelper.floor(((AbstractNbtNumber) tag).doubleValue());
        } else if (tag instanceof AbstractNbtList) {
            ret = ((AbstractNbtList<?>) tag).size();
        } else if (tag instanceof NbtCompound) {
            ret = ((NbtCompound) tag).getSize();
        } else if (tag instanceof NbtString) {
            ret = tag.asString().length();
        } else {
            throw GET_UNKNOWN_EXCEPTION.create(path.toString());
        }

        sendFeedback(dataObj.feedbackQuery(tag));
        return ret;
    }

    private static NbtElement getNbt(NbtPathArgumentType.NbtPath path, DataCommandObject dataObj) throws CommandSyntaxException {
        Collection<NbtElement> tags = path.get(dataObj.getNbt());
        Iterator<NbtElement> tagItr = tags.iterator();
        NbtElement firstTag = tagItr.next();
        if (tagItr.hasNext()) {
            throw GET_MULTIPLE_EXCEPTION.create();
        } else {
            return firstTag;
        }
    }

}
