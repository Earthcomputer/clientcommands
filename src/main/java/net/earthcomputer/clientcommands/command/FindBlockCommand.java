package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgument;
import net.earthcomputer.clientcommands.task.RenderDistanceScanTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgument.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FindBlockCommand {
    public static final Flag<Boolean> FLAG_KEEP_SEARCHING = Flag.ofFlag("keep-searching").build();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        var cfindblock = dispatcher.register(literal("cfindblock")
            .then(argument("block", withString(blockPredicate(context)))
                .executes(ctx -> {
                    var blockWithString = getWithString(ctx, "block", ClientBlockPredicateArgument.ParseResult.class);
                    return findBlock(ctx, Component.translatable("commands.cfindblock.starting", blockWithString.string()), getBlockPredicate(blockWithString.value()));
                })));
        FLAG_KEEP_SEARCHING.addToCommand(dispatcher, cfindblock, ctx -> true);
    }

    public static int findBlock(CommandContext<FabricClientCommandSource> ctx, Component startingMessage, ClientBlockPredicate block) throws CommandSyntaxException {
        boolean keepSearching = getFlag(ctx, FLAG_KEEP_SEARCHING);
        sendFeedback(startingMessage);
        TaskManager.addTask("cfindblock", new FindBlockTask(block, keepSearching));
        return Command.SINGLE_SUCCESS;
    }

    private static final class FindBlockTask extends RenderDistanceScanTask {
        private final ClientBlockPredicate predicate;

        @Nullable
        private BlockPos closestBlock;

        FindBlockTask(ClientBlockPredicate predicate, boolean keepSearching) {
            super(keepSearching);
            this.predicate = predicate;
        }

        @Override
        protected void scanBlock(Entity cameraEntity, BlockPos pos) throws CommandSyntaxException {
            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;
            Vec3 cameraPos = cameraEntity.getEyePosition(0);
            if ((closestBlock == null || pos.distToCenterSqr(cameraPos) < closestBlock.distToCenterSqr(cameraPos)) && predicate.test(level.registryAccess(), level, pos)) {
                closestBlock = pos.immutable();
                keepSearching = false;
            }
        }

        @Override
        protected boolean canScanChunk(Entity cameraEntity, ChunkPos pos) {
            return (closestBlock == null || hasAnyBlockCloserThan(cameraEntity, pos, closestBlock.distToCenterSqr(cameraEntity.getEyePosition(0))))
                && super.canScanChunk(cameraEntity, pos);
        }

        @Override
        protected boolean canScanChunkSection(Entity cameraEntity, SectionPos pos) {
            return hasBlockState(pos, predicate::canEverMatch) && super.canScanChunkSection(cameraEntity, pos);
        }

        @Override
        public void onCompleted() {
            super.onCompleted();
            if (closestBlock == null) {
                sendError(Component.translatable("commands.cfindblock.notFound"));
            } else {
                Entity cameraEntity = Objects.requireNonNullElse(Minecraft.getInstance().cameraEntity, Minecraft.getInstance().player);

                String foundRadius = "%.2f".formatted(Math.sqrt(closestBlock.distToCenterSqr(cameraEntity.getEyePosition(0))));
                sendFeedback(
                    Component.translatable(
                        "commands.cfindblock.success",
                        Component.empty()
                            .append(getLookCoordsTextComponent(closestBlock))
                            .append(" ")
                            .append(getGlowButtonTextComponent(closestBlock)),
                        foundRadius
                    )
                );
            }
        }
    }
}
