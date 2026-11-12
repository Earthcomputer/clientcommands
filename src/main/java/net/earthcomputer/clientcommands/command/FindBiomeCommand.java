package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.earthcomputer.clientcommands.task.RenderDistanceScanTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static dev.xpple.clientarguments.arguments.CResourceArgument.*;
import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.earthcomputer.clientcommands.command.arguments.WithStringArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class FindBiomeCommand {
    public static final Flag<Boolean> FLAG_KEEP_SEARCHING = Flag.ofFlag("keep-searching").build();

    @SuppressWarnings("unchecked")
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        var cfindbiome = dispatcher.register(literal("cfindbiome")
            .then(argument("biome", withString(resource(context, Registries.BIOME)))
                .executes(ctx -> {
                    var biomeWithString = getWithString(ctx, "biome", Holder.Reference.class);
                    return findBiome(ctx, Component.translatable("commands.cfindbiome.starting", biomeWithString.string()), (Holder.Reference<Biome>) biomeWithString.value());
                })));
        FLAG_KEEP_SEARCHING.addToCommand(dispatcher, cfindbiome, ctx -> true);
    }

    public static int findBiome(CommandContext<FabricClientCommandSource> ctx, Component startingMessage, Holder.Reference<Biome> biome) throws CommandSyntaxException {
        ClientCommandHelper.requirePrivileges();
        boolean keepSearching = getFlag(ctx, FLAG_KEEP_SEARCHING);
        sendFeedback(startingMessage);
        TaskManager.addTask("cfindbiome", new FindBiomeTask(biome, keepSearching));
        return Command.SINGLE_SUCCESS;
    }

    private static final class FindBiomeTask extends RenderDistanceScanTask {
        private final Holder.Reference<Biome> biome;

        @Nullable
        private BlockPos closestBiome;

        FindBiomeTask(Holder.Reference<Biome> biome, boolean keepSearching) {
            super(keepSearching);
            this.biome = biome;
        }

        // only scan biomes at quart scale
        @Override
        protected void scanChunkSection(Entity cameraEntity, SectionPos sectionPos) {
            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;

            int minX = sectionPos.minBlockX(), minY = sectionPos.minBlockY(), minZ = sectionPos.minBlockZ();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

            for (int y = 0; y < 4; y++) {
                pos.setY(minY + y * QuartPos.SIZE);
                for (int x = 0; x < 4; x++) {
                    pos.setX(minX + x * QuartPos.SIZE);
                    for (int z = 0; z < 4; z++) {
                        pos.setZ(minZ + z * QuartPos.SIZE);

                        scanBlock(cameraEntity, pos);
                    }
                }
            }
        }

        @Override
        protected boolean canScanChunk(Entity cameraEntity, ChunkPos pos) {
            return (closestBiome == null || hasAnyBlockCloserThan(cameraEntity, pos, closestBiome.distToCenterSqr(cameraEntity.getEyePosition(0))))
                && super.canScanChunk(cameraEntity, pos);
        }

        @Override
        protected boolean canScanChunkSection(Entity cameraEntity, SectionPos pos) {
            return hasBiome(pos, b -> b.is(biome)) && super.canScanChunkSection(cameraEntity, pos);
        }

        @Override
        protected void scanBlock(Entity cameraEntity, BlockPos pos) {
            ClientLevel level = Minecraft.getInstance().level;
            assert level != null;
            Holder<Biome> noiseBiome = level.getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()), QuartPos.fromBlock(pos.getZ()));
            Vec3 cameraPos = cameraEntity.getEyePosition(0);
            if ((closestBiome == null || pos.distToCenterSqr(cameraPos) < closestBiome.distToCenterSqr(cameraPos)) && noiseBiome.is(biome)) {
                closestBiome = pos.immutable();
                keepSearching = false;
            }
        }

        @Override
        public void onCompleted() {
            super.onCompleted();
            if (closestBiome == null) {
                sendError(Component.translatable("commands.cfindbiome.notFound"));
            } else {
                Entity cameraEntity = Objects.requireNonNullElse(Minecraft.getInstance().getCameraEntity(), Minecraft.getInstance().player);

                String foundRadius = "%.2f".formatted(Math.sqrt(closestBiome.distToCenterSqr(cameraEntity.getEyePosition(0))));
                sendFeedback(
                    Component.translatable(
                        "commands.cfindbiome.success",
                        Component.empty()
                            .append(CComponentUtil.getLookCoordsTextComponent(closestBiome))
                            .append(" ")
                            .append(CComponentUtil.getGlowButtonTextComponent(closestBiome)),
                        foundRadius
                    )
                );
            }
        }
    }
}
