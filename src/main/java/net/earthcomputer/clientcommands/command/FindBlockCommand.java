package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType;
import net.earthcomputer.clientcommands.task.RenderDistanceScanTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.ClientBlockPredicateArgumentType.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class FindBlockCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("cfindblock")
            .then(argument("block", withString(blockPredicate(registryAccess)))
                .executes(ctx -> {
                    var blockWithString = getWithString(ctx, "block", ClientBlockPredicateArgumentType.ParseResult.class);
                    return findBlock(Text.translatable("commands.cfindblock.starting", blockWithString.getLeft()), getBlockPredicate(blockWithString.getRight()));
                })));
    }

    public static int findBlock(Text startingMessage, ClientBlockPredicate block) {
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
            ClientWorld world = MinecraftClient.getInstance().world;
            assert world != null;
            Vec3d cameraPos = cameraEntity.getCameraPosVec(0);
            if ((closestBlock == null || pos.getSquaredDistance(cameraPos) < closestBlock.getSquaredDistance(cameraPos)) && predicate.test(world, pos)) {
                closestBlock = pos.toImmutable();
            }
        }

        @Override
        protected boolean canScanChunk(Entity cameraEntity, ChunkPos pos) {
            return (closestBlock == null || hasAnyBlockCloserThan(cameraEntity, pos, closestBlock.getSquaredDistance(cameraEntity.getCameraPosVec(0))))
                && super.canScanChunk(cameraEntity, pos);
        }

        @Override
        protected boolean canScanChunkSection(Entity cameraEntity, ChunkSectionPos pos) {
            return hasBlockState(pos, predicate::canEverMatch) && super.canScanChunkSection(cameraEntity, pos);
        }

        @Override
        public void onCompleted() {
            if (closestBlock == null) {
                sendError(Text.translatable("commands.cfindblock.notFound"));
            } else {
                Entity cameraEntity = Objects.requireNonNullElse(MinecraftClient.getInstance().cameraEntity, MinecraftClient.getInstance().player);

                String foundRadius = "%.2f".formatted(Math.sqrt(closestBlock.getSquaredDistance(cameraEntity.getCameraPosVec(0))));
                sendFeedback(Text.translatable("commands.cfindblock.success.left", foundRadius)
                    .append(getLookCoordsTextComponent(closestBlock))
                    .append(" ")
                    .append(getGlowCoordsTextComponent(Text.translatable("commands.cfindblock.success.glow"), closestBlock))
                    .append(Text.translatable("commands.cfindblock.success.right", foundRadius)));
            }
        }
    }
}
