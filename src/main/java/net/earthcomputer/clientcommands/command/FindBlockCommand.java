package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType;
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
import static net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FindBlockCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("cfindblock")
            .then(argument("block", withString(blockPredicate(context)))
                .executes(ctx -> {
                    var blockWithString = getWithString(ctx, "block", ClientBlockPredicateArgumentType.ParseResult.class);
                    return findBlock(Component.translatable("commands.cfindblock.starting", blockWithString.getLeft()), getBlockPredicate(blockWithString.getRight()));
                })));
    }

    public static int findBlock(Component startingMessage, ClientBlockPredicate block) {
        sendFeedback(startingMessage);
        TaskManager.addTask("cfindblock", new FindBlockTask(block));
        return Command.SINGLE_SUCCESS;
    }

    private static final class FindBlockTask extends RenderDistanceScanTask {
        private final ClientBlockPredicate predicate;

        @Nullable
        private BlockPos closestBlock;

        FindBlockTask(ClientBlockPredicate predicate) {
            super(false);
            this.predicate = predicate;
        }

        @Override
        protected void scanBlock(Entity cameraEntity, BlockPos pos) {
            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;
            Vec3 cameraPos = cameraEntity.getEyePosition(0);
            if ((closestBlock == null || pos.distToCenterSqr(cameraPos) < closestBlock.distToCenterSqr(cameraPos)) && predicate.test(level, pos)) {
                closestBlock = pos.immutable();
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
            if (closestBlock == null) {
                sendError(Component.translatable("commands.cfindblock.notFound"));
            } else {
                Entity cameraEntity = Objects.requireNonNullElse(Minecraft.getInstance().cameraEntity, Minecraft.getInstance().player);

                String foundRadius = "%.2f".formatted(Math.sqrt(closestBlock.distToCenterSqr(cameraEntity.getEyePosition(0))));
                sendFeedback(Component.translatable("commands.cfindblock.success.left", foundRadius)
                    .append(getLookCoordsTextComponent(closestBlock))
                    .append(" ")
                    .append(getGlowCoordsTextComponent(Component.translatable("commands.cfindblock.success.glow"), closestBlock))
                    .append(Component.translatable("commands.cfindblock.success.right", foundRadius)));
            }
        }
    }
}
