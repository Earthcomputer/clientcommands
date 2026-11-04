package net.earthcomputer.clientcommands.server;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class FindItemUtil {
    private FindItemUtil() {
    }

    public static int countItems(ItemStackTemplate stack, Predicate<ItemStackTemplate> searchingFor, boolean searchNested) {
        if (searchingFor.test(stack)) {
            return stack.count();
        }

        if (searchNested) {
            int total = 0;

            ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
            if (containerContents != null) {
                for (ItemStackTemplate item : containerContents.nonEmptyItems()) {
                    total += countItems(item, searchingFor, true);
                }
            }

            BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundleContents != null) {
                for (ItemStackTemplate item : bundleContents.items()) {
                    total += countItems(item, searchingFor, true);
                }
            }

            return total;
        }

        return 0;
    }

    public static int countItems(Iterable<ItemStack> stacks, Predicate<ItemStackTemplate> searchingFor, boolean searchNested) {
        int total = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                total += countItems(ItemStackTemplate.fromNonEmptyStack(stack), searchingFor, searchNested);
            }
        }
        return total;
    }

    public static @Nullable Container getSearchableContainer(Entity entity) {
        return switch (entity) {
            // reading the items would generate the loot
            case ContainerEntity containerEntity -> containerEntity.getContainerLootTable() == null ? containerEntity : null;
            case AbstractChestedHorse horse -> horse.hasChest() ? horse.inventory : null;
            default -> null;
        };
    }

    public static @Nullable Container getSearchableContainer(BlockEntity blockEntity) {
        if (!(blockEntity instanceof Container container)) {
            return null;
        }
        // reading the items would generate the loot
        if (container instanceof RandomizableContainer randomizable && randomizable.getLootTable() != null) {
            return null;
        }
        return container;
    }

    public static boolean isEnderChest(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof EnderChestBlockEntity;
    }

    public static LongList getChunksAround(ChunkPos center, int radius) {
        LongList chunks = new LongArrayList();
        for (BlockPos pos : BlockPos.spiralAround(new BlockPos(center.x(), 0, center.z()), radius, Direction.EAST, Direction.SOUTH)) {
            chunks.add(ChunkPos.pack(pos.getX(), pos.getZ()));
        }
        return chunks;
    }

    public abstract static class ChunkSearcher {
        private final Set<BlockPos> searchedBlocks = new HashSet<>();
        private final Set<UUID> searchedEntities = new HashSet<>();
        @Nullable
        private BlockPos closestEnderChest;

        protected abstract void searchBlock(BlockPos pos, Container container);

        protected abstract void searchEntity(Entity entity, Container container);

        public @Nullable BlockPos getClosestEnderChest() {
            return closestEnderChest;
        }

        public void searchChunk(Level level, LevelChunk chunk, Vec3 origin) {
            for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                BlockPos pos = blockEntity.getBlockPos();
                if (isEnderChest(blockEntity)) {
                    if (searchedBlocks.add(pos) && (closestEnderChest == null || pos.distToCenterSqr(origin) < closestEnderChest.distToCenterSqr(origin))) {
                        closestEnderChest = pos;
                    }
                    continue;
                }
                Container container = getSearchableContainer(blockEntity);
                if (container != null && searchedBlocks.add(pos)) {
                    searchBlock(pos, container);
                }
            }

            ChunkPos chunkPos = chunk.getPos();
            AABB chunkBox = new AABB(chunkPos.getMinBlockX(), level.getMinY(), chunkPos.getMinBlockZ(), chunkPos.getMaxBlockX() + 1, level.getMaxY() + 1, chunkPos.getMaxBlockZ() + 1);
            for (Entity entity : level.getEntities((Entity) null, chunkBox, entity -> getSearchableContainer(entity) != null)) {
                Container container = getSearchableContainer(entity);
                if (container != null && searchedEntities.add(entity.getUUID())) {
                    searchEntity(entity, container);
                }
            }
        }
    }
}
