package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.earthcomputer.clientcommands.render.Cuboid;
import net.earthcomputer.clientcommands.render.Renderer;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.entities;
import static net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType.getEntitySelector;
import static net.earthcomputer.clientcommands.command.arguments.MultibaseIntegerArgumentType.getMultibaseInteger;
import static net.earthcomputer.clientcommands.command.arguments.MultibaseIntegerArgumentType.multibaseInteger;
import static net.minecraft.command.argument.BlockPosArgumentType.blockPos;
import static net.minecraft.command.argument.BlockPosArgumentType.getBlockPos;
import static net.minecraft.command.argument.ColorArgumentType.color;
import static net.minecraft.command.argument.ColorArgumentType.getColor;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class GlowCommand {
    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cglow.failed"));

    private static final int FLAG_KEEP_SEARCHING = 1;

    static List<Renderer> renderers = new ArrayList<>();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cglow");

        LiteralCommandNode<ServerCommandSource> cglow = dispatcher.register(literal("cglow"));
        dispatcher.register(literal("cglow")
            .then(literal("entity")
                .then(literal("--keep-searching")
                    .redirect(cglow, ctx -> withFlags(ctx.getSource(), FLAG_KEEP_SEARCHING, true)))
                .then(argument("targets", entities())
                    .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getFlag(ctx, FLAG_KEEP_SEARCHING) ? 0 : 30, 0xffffff))
                    .then(argument("seconds", integer(0))
                        .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getInteger(ctx, "seconds"), 0xffffff))
                        .then(literal("color")
                            .then(argument("color", color())
                                .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getInteger(ctx, "seconds"), Optional.ofNullable(getColor(ctx, "color").getColorValue()).orElse(0xffffff)))))
                        .then(literal("colorCode")
                            .then(argument("color", multibaseInteger(0, 0xffffff))
                                .executes(ctx -> glowEntities(ctx.getSource(), getEntitySelector(ctx, "targets"), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color"))))))))
            .then(literal("block")
                .then(argument("target", blockPos())
                .executes(ctx -> glowBlock(ctx.getSource(), getBlockPos(ctx, "target"), 30, 0xffffff))
                    .then(argument("seconds", integer(1))
                        .executes(ctx -> glowBlock(ctx.getSource(), getBlockPos(ctx, "target"), getInteger(ctx, "seconds"), 0xffffff))
                        .then(literal("color")
                            .then(argument("color", color())
                                .executes(ctx -> glowBlock(ctx.getSource(), getBlockPos(ctx, "target"), getInteger(ctx, "seconds"), Optional.ofNullable(getColor(ctx, "color").getColorValue()).orElse(0xffffff)))))
                        .then(literal("colorCode")
                            .then(argument("color", multibaseInteger(0, 0xffffff))
                                .executes(ctx -> glowBlock(ctx.getSource(), getBlockPos(ctx, "target"), getInteger(ctx, "seconds"), getMultibaseInteger(ctx, "color")))))))));
    }

    private static int glowEntities(ServerCommandSource source, ClientEntitySelector entitySelector, int seconds, int color) throws CommandSyntaxException {
        boolean keepSearching = getFlag(source, FLAG_KEEP_SEARCHING);
        if (keepSearching) {
            String taskName = TaskManager.addTask("cglow", new LongTask() {
                @Override
                public void initialize() {
                }

                @Override
                public boolean condition() {
                    return MinecraftClient.getInstance().player != null;
                }

                @Override
                public void increment() {
                }

                @Override
                public void body() {
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    assert player != null;
                    for (Entity entity : entitySelector.getEntities(new FakeCommandSource(player))) {
                        ((IEntity) entity).addGlowingTicket(seconds * 20, color);
                    }
                    scheduleDelay();
                }
            });

            sendFeedback(new TranslatableText("commands.cglow.keepSearching.success")
                    .append(" ")
                    .append(getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));

            return 0;
        } else {
            List<Entity> entities = entitySelector.getEntities(source);
            if (entities.isEmpty()) {
                throw FAILED_EXCEPTION.create();
            }

            for (Entity entity : entities) {
                ((IEntity) entity).addGlowingTicket(seconds * 20, color);
            }

            sendFeedback("commands.cglow.success", entities.size());

            return entities.size();
        }
    }

    private static int glowBlock(ServerCommandSource source, BlockPos block, int seconds, int color) {
        for (Box box : source.getEntity().getEntityWorld().getBlockState(block).getOutlineShape(source.getWorld(), block).getBoundingBoxes()) {
            renderers.add(new Cuboid(box.offset(block), new Color(color), seconds * 20));
        }
        return 0;
    }

    public static void renderBlockGlow(MatrixStack matrixStack) {
        if (renderers == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.multMatrix(matrixStack.peek().getModel());

        GlStateManager.disableTexture();

        //Makes it render through blocks.
        GlStateManager.disableDepthTest();

        for (Iterator<Renderer> iterator = renderers.iterator(); iterator.hasNext(); ) {
            Renderer renderer = iterator.next();
            renderer.render();
            if (renderer.shouldKill()) {
                // Remove the current element from the iterator and the list.
                iterator.remove();
            }
        }

        GlStateManager.popMatrix();
    }

}
