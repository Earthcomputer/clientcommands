package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.seedfinding.mcfeature.loot.LootContext;
import com.seedfinding.mcfeature.loot.LootTable;
import com.seedfinding.mcfeature.loot.MCLootTables;
import com.seedfinding.mcfeature.loot.item.ItemStack;
import net.earthcomputer.clientcommands.task.RenderDistanceScanTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.earthcomputer.clientcommands.util.SeedfindingUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class PredictBrushablesCommand {
    public static final Flag<Boolean> FLAG_KEEP_SEARCHING = Flag.ofFlag("keep-searching").build();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var cpredictbrushables = dispatcher.register(literal("cpredictbrushables")
            .executes(PredictBrushablesCommand::predictBrushables));
        FLAG_KEEP_SEARCHING.addToCommand(dispatcher, cpredictbrushables, ctx -> true);
    }

    private static int predictBrushables(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        boolean keepSearching = getFlag(ctx, FLAG_KEEP_SEARCHING);
        String taskName = TaskManager.addTask("cpredictbrushables", new PredictBrushablesTask(keepSearching));
        if (keepSearching) {
            sendFeedback(Component.translatable("commands.cpredictbrushables.starting.keepSearching", CComponentUtil.getCommandTextComponent("commands.client.cancel", "/ctask stop " + taskName)));
        } else {
            sendFeedback(Component.translatable("commands.cpredictbrushables.starting"));
        }

        return Command.SINGLE_SUCCESS;
    }

    private static final class PredictBrushablesTask extends RenderDistanceScanTask {
        private boolean found = false;

        PredictBrushablesTask(boolean keepSearching) {
            super(keepSearching);
        }

        @Override
        protected void scanBlock(Entity cameraEntity, BlockPos pos) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            ClientLevel level = minecraft.level;
            assert level != null;
            BlockState blockState = level.getBlockState(pos);

            // best effort, may be inaccurate
            Supplier<LootTable> lootTableSupplier;
            long lootSeed;

            if (blockState.is(Blocks.SUSPICIOUS_SAND)) {
                Holder<Biome> biome = level.getBiome(pos);
                if (biome.is(Biomes.DESERT)) {
                    // either desert well or desert pyramid, check for water
                    boolean hasWater = Stream.of(pos.atY(pos.getY() + 1), pos.atY(pos.getY() + 2))
                        .map(level::getBlockState)
                        .anyMatch(s -> s.is(Blocks.WATER));
                    lootTableSupplier = hasWater ? MCLootTables.DESERT_WELL_ARCHAEOLOGY : MCLootTables.DESERT_PYRAMID_ARCHAEOLOGY;
                    lootSeed = pos.asLong();
                } else if (biome.is(BiomeTags.HAS_OCEAN_RUIN_WARM)) {
                    lootTableSupplier = MCLootTables.OCEAN_RUIN_WARM_ARCHAEOLOGY;
                    RandomSource randomSource = RandomSource.create(Mth.getSeed(pos));
                    lootSeed = randomSource.nextLong();
                } else {
                    return;
                }
            } else if (blockState.is(Blocks.SUSPICIOUS_GRAVEL)) {
                Holder<Biome> biome = level.getBiome(pos);
                if (biome.is(BiomeTags.HAS_OCEAN_RUIN_COLD)) {
                    lootTableSupplier = MCLootTables.OCEAN_RUIN_COLD_ARCHAEOLOGY;
                    RandomSource randomSource = RandomSource.create(Mth.getSeed(pos));
                    lootSeed = randomSource.nextLong();
                } else if (biome.is(BiomeTags.HAS_TRAIL_RUINS)) {
                    // TODO: check for rare or common loot
                    // Common loot is generated in TRAIL_RUINS_HOUSES_ARCHAEOLOGY,
                    // TRAIL_RUINS_ROADS_ARCHAEOLOGY and TRAIL_RUINS_TOWER_TOP_ARCHAEOLOGY
                    // Rare is generated in TRAIL_RUINS_HOUSES_ARCHAEOLOGY
                    // Ignored for now
                    return;
                } else {
                    return;
                }
            } else {
                return;
            }

            LootContext context = new LootContext(lootSeed, SeedfindingUtil.getMCVersion())
                .withLuck((int) player.getLuck());

            List<ItemStack> items = lootTableSupplier.get().generate(context);
            for (ItemStack item : items) {
                var mcItemStack = SeedfindingUtil.fromSeedfindingItem(item, level.registryAccess());
                mcItemStack.set(DataComponents.LORE, getItemLore(mcItemStack));
                ClientCommandHelper.sendFeedback(Component.translatable("commands.cpredictbrushables.foundBrushableBlock", blockState.getBlock().getName(), CComponentUtil.getLookCoordsTextComponent(pos), mcItemStack.getDisplayName(), CComponentUtil.getGlowButtonTextComponent(pos)));
            }

            found = true;
        }

        // use lore to display effects
        private static ItemLore getItemLore(net.minecraft.world.item.ItemStack itemStack) {
            SuspiciousStewEffects effects = itemStack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
            if (effects == null) {
                return ItemLore.EMPTY;
            }
            List<Component> components = new ArrayList<>();
            for (SuspiciousStewEffects.Entry entry : effects.effects()) {
                MobEffectInstance mobEffectInstance = entry.createEffectInstance();
                MutableComponent description = PotionContents.getPotionDescription(mobEffectInstance.getEffect(), mobEffectInstance.getAmplifier());
                Component line = Component.translatable("commands.cpredictbrushables.stewEffect", description, entry.duration() / SharedConstants.TICKS_PER_SECOND);
                components.add(line);
            }
            return new ItemLore(components);
        }

        @Override
        protected void onBlockStateUpdate(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        }

        @Override
        protected boolean canScanChunkSection(Entity cameraEntity, SectionPos pos) {
            return hasBlockState(pos, s -> s.getBlock() instanceof BrushableBlock) && super.canScanChunkSection(cameraEntity, pos);
        }

        @Override
        public void onCompleted() {
            super.onCompleted();
            if (!found) {
                sendError(Component.translatable("commands.cpredictbrushables.notFound"));
            }
        }
    }
}
