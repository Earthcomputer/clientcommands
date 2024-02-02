package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.blockPos;
import static dev.xpple.clientarguments.arguments.CBlockPosArgumentType.getCBlockPos;
import static dev.xpple.clientarguments.arguments.CColorArgumentType.color;
import static dev.xpple.clientarguments.arguments.CColorArgumentType.getCColor;
import static dev.xpple.clientarguments.arguments.CEntityArgumentType.entities;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.getCommandTextComponent;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.getFlag;
import static net.earthcomputer.clientcommands.command.arguments.MultibaseIntegerArgumentType.getMultibaseInteger;
import static net.earthcomputer.clientcommands.command.arguments.MultibaseIntegerArgumentType.multibaseInteger;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class GlowCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cglow.entity.failed"));

    private static final Flag<Boolean> FLAG_KEEP_SEARCHING = Flag.ofFlag("keep-searching").build();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cglow = dispatcher.register(literal("cglow")
            .then(literal("entities")
                .then(argument("targets", entities())
                    .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getFlag(ctx, FLAG_KEEP_SEARCHING) ? 0 : 30, 0xffffff))
                    .then(argument("seconds", integer(0))
                        .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getInteger(ctx, "seconds"), 0xffffff))
                        .then(literal("color")
                            .then(argument("color", color())
                                .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getInteger(ctx, "seconds"), Optional.ofNullable(getCColor(ctx, "color").getColor()).orElse(0xffffff)))))
                        .then(literal("colorCode")
                            .then(argument("color", multibaseInteger(0, 0xffffff))
                                .executes(ctx -> glowEntities(ctx.getSource(), ctx.getArgument("targets", CEntitySelector.class), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color"))))))))
            .then(literal("area")
                .then(argument("from", blockPos())
                    .then(argument("to", blockPos())
                        .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), 1, 0xffffff))
                        .then(argument("seconds", integer(0))
                            .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getInteger(ctx, "seconds"), 0xffffff))
                            .then(literal("color")
                                .then(argument("color", color())
                                    .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getInteger(ctx, "seconds"), Optional.ofNullable(getCColor(ctx, "color").getColor()).orElse(0xffffff)))))
                            .then(literal("colorCode")
                                .then(argument("color", multibaseInteger(0, 0xffffff))
                                    .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "from"), getCBlockPos(ctx, "to"), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color")))))))))
            .then(literal("block")
                .then(argument("block", blockPos())
                    .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, 1, 0xffffff))
                    .then(argument("seconds", integer(0))
                        .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, getInteger(ctx, "seconds"), 0xffffff))
                        .then(literal("color")
                            .then(argument("color", color())
                                .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, getInteger(ctx, "seconds"), Optional.ofNullable(getCColor(ctx, "color").getColor()).orElse(0xffffff)))))
                        .then(literal("colorCode")
                            .then(argument("color", multibaseInteger(0, 0xffffff))
                                .executes(ctx -> glowBlock(ctx.getSource(), getCBlockPos(ctx, "block"), null, getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color")))))))));
        FLAG_KEEP_SEARCHING.addToCommand(dispatcher, cglow, ctx -> true);
    }

    private static int glowEntities(FabricClientCommandSource source, CEntitySelector entitySelector, int seconds, int color) throws CommandSyntaxException {
        boolean keepSearching = getFlag(source, FLAG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cglow", new SimpleTask() {
                @Override
                public boolean condition() {
                    return Minecraft.getInstance().player != null;
                }

                @Override
                protected void onTick() {
                    LocalPlayer player = Minecraft.getInstance().player;
                    assert player != null;

                    try {
                        for (Entity entity : entitySelector.getEntities(source)) {
                            ((IEntity) entity).addGlowingTicket(seconds * 20, color);
                        }
                    } catch (CommandSyntaxException e) {
                        e.printStackTrace();
                    }
                }
            });

            source.sendFeedback(Component.translatable("commands.cglow.entity.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return Command.SINGLE_SUCCESS;
        } else {
            List<? extends Entity> entities = entitySelector.getEntities(source);
            if (entities.isEmpty()) {
                throw FAILED_EXCEPTION.create();
            }

            for (Entity entity : entities) {
                ((IEntity) entity).addGlowingTicket(seconds * 20, color);
            }

            source.sendFeedback(Component.translatable("commands.cglow.entity.success", entities.size()));

            return entities.size();
        }
    }

    private static int glowBlock(FabricClientCommandSource source, BlockPos pos1, BlockPos pos2, int seconds, int color) {
        List<AABB> boundingBoxes = new ArrayList<>();

        if (pos2 == null) {
            boundingBoxes.addAll(Minecraft.getInstance().level.getBlockState(pos1).getShape(source.getWorld(), pos1).toAabbs());
            if (boundingBoxes.isEmpty()) {
                boundingBoxes.add(new AABB(pos1));
            } else {
                boundingBoxes.replaceAll((box) -> box.move(pos1));
            }
        } else {
            boundingBoxes.add(AABB.encapsulatingFullBlocks(pos1, pos2));
        }

        for (AABB box : boundingBoxes) {
            RenderQueue.addCuboid(RenderQueue.Layer.ON_TOP, box, box, color, seconds * 20);
        }

        source.sendFeedback(Component.translatable("commands.cglow.area.success", boundingBoxes.size()));

        return boundingBoxes.size();
    }
}
