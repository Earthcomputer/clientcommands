package net.earthcomputer.clientcommands.gametest;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.xpple.betterconfig.api.BetterConfigAPI;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.features.ChorusManipulation;
import net.earthcomputer.clientcommands.features.EnchantmentCracker;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.gametest.mixin.ChatComponentAccessor;
import net.earthcomputer.clientcommands.server.ServerConfigs;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("UnstableApiUsage")
public class ClientCommandsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    // The default superflat world has its grass layer at y=-61
    static final int GRASS_Y = -61;
    static final int PLAYER_Y = GRASS_Y + 1;
    static final BlockPos SPAWN_POS = new BlockPos(0, PLAYER_Y, 0);
    private static final List<String> failedTests = new ArrayList<>();
    private static final List<PlayerRandCracker.RNGCallType> rngCalls = new ArrayList<>();
    private static boolean registeredRngCallListener = false;
    private static final List<Class<?>> TEST_CLASSES = List.of(
        ChorusManipulationTests.class,
        CrackRngTests.class,
        EnchantmentCrackingTests.class,
        FindItemTests.class,
        PlayerRandResetTests.class
    );

    static void runTest(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, Runnable testRunner) {
        runTest(context, singleplayerContext, null, testRunner);
    }

    static void runTest(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, @Nullable String variant, Runnable testRunner) {
        commonSetup(context, singleplayerContext);

        boolean testFailed = false;
        try {
            testRunner.run();
        } catch (Throwable e) {
            testFailed = true;
            LOGGER.error("Test failed", e);
        }

        context.waitTick();
        String testName = getCallingTest().getName() + (variant == null ? "" : "(" + variant + ")");
        reportTestResult(context, testName, testFailed);
    }

    private static void commonSetup(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        singleplayerContext.getServer().waitFor(_ -> singleplayerContext.getConnection().getServerPlayer().connection.hasClientLoaded());
        context.runOnClient(client -> {
            client.gui.setScreen(null);
            for (String taskName : Lists.newArrayList(TaskManager.getTaskNames())) {
                TaskManager.removeTask(taskName);
            }
            Objects.requireNonNull(client.player).setDeltaMovement(Vec3.ZERO);
        });
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        singleplayerContext.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            ServerLevel level = server.overworld();
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
            }
            player.stopRiding();
            player.stopFallFlying();
            player.setShoulderParrotLeft(Optional.empty());
            player.setShoulderParrotRight(Optional.empty());
            player.setGameMode(GameType.SURVIVAL);
            teleport(level, player, Vec3.atBottomCenterOf(SPAWN_POS), 0, 0);
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.setInvulnerableTime(0);
            player.getFoodData().setFoodLevel(20);
            player.setAirSupply(player.getMaxAirSupply());
            player.clearFire();
            player.setExperienceLevels(0);
            // e.g. a shield disabled by an axe in a previous test
            ItemStack shield = new ItemStack(Items.SHIELD);
            player.getCooldowns().removeCooldown(player.getCooldowns().getCooldownGroup(shield));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }

            fill(level, SPAWN_POS.offset(-16, 0, -16), SPAWN_POS.offset(16, 16, 16), Blocks.AIR.defaultBlockState());
            fill(level, new BlockPos(-16, GRASS_Y - 2, -16), new BlockPos(16, GRASS_Y - 1, 16), Blocks.DIRT.defaultBlockState());
            fill(level, new BlockPos(-16, GRASS_Y, -16), new BlockPos(16, GRASS_Y, 16), Blocks.GRASS_BLOCK.defaultBlockState());

            List<Entity> entities = new ArrayList<>();
            level.getAllEntities().forEach(entities::add);
            for (Entity entity : entities) {
                if (!(entity instanceof Player)) {
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        });
        clearInventory(singleplayerContext);
        setServerFindItem(singleplayerContext, true);
        singleplayerContext.getConnection().waitForClientboundPackets();
        singleplayerContext.getConnection().waitForChunksDownload();
        context.waitTick();
        context.runOnClient(_ -> {
            clearProtocolVersionOverride();
            Configs.playerRNGMaintenance = true;
            Configs.enchantingPrediction = true;
            Configs.chorusManipulation = false;
            ChorusManipulation.chorusGoalFrom = null;
            ChorusManipulation.chorusGoalTo = null;
            Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
            EnchantmentCracker.resetCracker();
            EnchantmentCracker.enchantingTablePos = null;
            rngCalls.clear();
        });
    }

    private static void reportTestResult(ClientGameTestContext context, String testName, boolean testFailed) {
        if (testFailed) {
            failedTests.add(testName);
            context.runOnClient(client -> client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test failed: " + testName).withStyle(ChatFormatting.RED)));
        } else {
            context.runOnClient(client -> client.gui.hud.getChat().addClientSystemMessage(Component.literal("Test passed: " + testName).withStyle(ChatFormatting.GREEN)));
        }
    }

    // Pretends to be connected to a server with the given protocol version and name
    static void setProtocolVersion(ClientGameTestContext context, int protocolVersion, String protocolName) {
        context.runOnClient(_ -> ((MultiVersionCompat.GameTestDebug) MultiVersionCompat.INSTANCE).setProtocolVersion(protocolVersion, protocolName));
    }

    static void clearProtocolVersionOverride() {
        ((MultiVersionCompat.GameTestDebug) MultiVersionCompat.INSTANCE).clearProtocolVersionOverride();
    }

    static boolean isProtocolVersionOverridden(ClientGameTestContext context) {
        return context.computeOnClient(_ -> ((MultiVersionCompat.GameTestDebug) MultiVersionCompat.INSTANCE).hasProtocolVersionOverride());
    }

    // Sets the server player's RNG seed and tells the client about it, as if the player RNG had been cracked
    static void setKnownPlayerSeed(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, long seed) {
        singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().getRandom().setSeed(seed));
        context.runOnClient(_ -> {
            PlayerRandCracker.setSeed((seed ^ PlayerRandCracker.MULTIPLIER) & PlayerRandCracker.MASK);
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
        });
    }

    static long getServerPlayerSeed(TestSingleplayerContext singleplayerContext) {
        return singleplayerContext.getServer().computeOnServer(_ ->
            ((LegacyRandomSource) singleplayerContext.getConnection().getServerPlayer().getRandom()).seed.get()
        );
    }

    static void assertPlayerSeedInSync(ClientGameTestContext context, TestSingleplayerContext singleplayerContext) {
        singleplayerContext.getConnection().waitForServerboundPackets();
        long serverSeed = getServerPlayerSeed(singleplayerContext);
        long clientSeed = context.computeOnClient(_ -> PlayerRandCracker.getSeed());
        assertEquals(serverSeed, clientSeed, () -> "Client player seed out of sync with server: server=0x" + Long.toHexString(serverSeed) + ", client=0x" + Long.toHexString(clientSeed));
    }

    static PlayerRandCracker.CrackState getPlayerCrackState(ClientGameTestContext context) {
        return context.computeOnClient(_ -> Configs.playerCrackState);
    }

    static void assertPlayerCrackState(ClientGameTestContext context, PlayerRandCracker.CrackState expected) {
        PlayerRandCracker.CrackState actual = getPlayerCrackState(context);
        assertEquals(expected, actual, () -> "Expected player crack state " + expected + ", got " + actual);
    }

    static void clearRngCalls(ClientGameTestContext context) {
        context.runOnClient(_ -> rngCalls.clear());
    }

    static List<PlayerRandCracker.RNGCallType> getRngCalls(ClientGameTestContext context) {
        return context.computeOnClient(_ -> List.copyOf(rngCalls));
    }

    static void setServerFindItem(TestSingleplayerContext singleplayerContext, boolean serverFindItem) {
        singleplayerContext.getServer().runOnServer(_ -> {
            if (ServerConfigs.serverFindItem != serverFindItem) {
                try {
                    BetterConfigAPI.getInstance().getModConfig(ServerConfigs.MOD_ID).set("serverFindItem", serverFindItem);
                } catch (CommandSyntaxException e) {
                    throw sneakyThrow(e);
                }
            }
        });
        // the change is applied in a task scheduled on the server, then sent to the client
        singleplayerContext.getServer().runOnServer(_ -> {});
        singleplayerContext.getConnection().waitForClientboundPackets();
    }

    static void clearChat(ClientGameTestContext context) {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
    }

    private static List<GuiMessage> getChatMessages(ClientGameTestContext context) {
        return context.computeOnClient(client -> List.copyOf(((ChatComponentAccessor) client.gui.hud.getChat()).getAllMessages()));
    }

    static boolean chatContains(ClientGameTestContext context, String translationKey) {
        return getChatMessages(context).stream().anyMatch(message -> findTranslatable(message.content(), translationKey) != null);
    }

    // Finds the given argument of the most recent chat message with the given translation key
    static Object findTranslationArg(ClientGameTestContext context, String translationKey, int argIndex) {
        for (GuiMessage message : getChatMessages(context)) {
            TranslatableContents translatable = findTranslatable(message.content(), translationKey);
            if (translatable != null) {
                Object arg = translatable.getArgs()[argIndex];
                return arg instanceof Component component ? component.getString() : arg;
            }
        }
        throw new AssertionError("No chat message with translation key " + translationKey);
    }

    private static @Nullable TranslatableContents findTranslatable(Component component, String translationKey) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            if (translatable.getKey().equals(translationKey)) {
                return translatable;
            }
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent) {
                    TranslatableContents result = findTranslatable(argComponent, translationKey);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            TranslatableContents result = findTranslatable(sibling, translationKey);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    static void runClientCommand(ClientGameTestContext context, String command) {
        context.runOnClient(client -> Objects.requireNonNull(client.player).connection.sendCommand(command));
    }

    static void useItemOnBlock(ClientGameTestContext context, BlockPos pos) {
        context.runOnClient(client -> {
            BlockHitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            Objects.requireNonNull(client.gameMode).useItemOn(Objects.requireNonNull(client.player), InteractionHand.MAIN_HAND, hitResult);
        });
    }

    static void useUntilConsumed(ClientGameTestContext context, TestSingleplayerContext singleplayerContext, Item item) {
        singleplayerContext.getConnection().waitForClientboundPackets();
        context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        try {
            singleplayerContext.getServer().waitFor(_ -> !singleplayerContext.getConnection().getServerPlayer().getInventory().getSelectedItem().is(item), 100);
        } finally {
            context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
        }
    }

    static void assertTrue(boolean condition, Supplier<String> message) {
        if (!condition) {
            throw new AssertionError(message.get());
        }
    }

    static void assertEquals(@Nullable Object expected, @Nullable Object actual, Supplier<String> message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message.get());
        }
    }

    static ItemStack enchantedItem(TestSingleplayerContext singleplayerContext, Item item, ResourceKey<Enchantment> enchantment, int level) {
        return singleplayerContext.getServer().computeOnServer(server -> {
            ItemStack stack = new ItemStack(item);
            stack.enchant(server.registryAccess().getOrThrow(enchantment), level);
            return stack;
        });
    }

    static void equip(TestSingleplayerContext singleplayerContext, EquipmentSlot slot, ItemStack stack) {
        singleplayerContext.getServer().runOnServer(_ -> singleplayerContext.getConnection().getServerPlayer().setItemSlot(slot, stack));
    }

    static void give(TestSingleplayerContext singleplayerContext, ItemLike item) {
        give(singleplayerContext, item, 1);
    }

    static void give(TestSingleplayerContext singleplayerContext, ItemLike item, int count) {
        give(singleplayerContext, new ItemStack(item, count));
    }

    static void give(TestSingleplayerContext singleplayerContext, ItemStack stack) {
        singleplayerContext.getServer().runOnServer(_ -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            int remaining = stack.getCount();
            while (remaining > 0) {
                int size = Math.min(stack.getMaxStackSize(), remaining);
                remaining -= size;
                ItemStack toAdd = stack.copyWithCount(size);
                if (!player.getInventory().add(toAdd) || !toAdd.isEmpty()) {
                    throw new IllegalStateException("Not enough inventory space to give " + stack);
                }
            }
            player.containerMenu.broadcastChanges();
        });
    }

    static void clearInventory(TestSingleplayerContext singleplayerContext) {
        singleplayerContext.getServer().runOnServer(_ -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            Inventory inventory = player.getInventory();
            inventory.clearContent();
            inventory.setSelectedSlot(0);
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.slotsChanged(inventory);
        });
    }

    static void setBlock(TestSingleplayerContext singleplayerContext, BlockPos pos, Block block) {
        setBlock(singleplayerContext, pos, block.defaultBlockState());
    }

    static void setBlock(TestSingleplayerContext singleplayerContext, BlockPos pos, BlockState state) {
        fill(singleplayerContext, pos, pos, state);
    }

    static void fill(TestSingleplayerContext singleplayerContext, BlockPos from, BlockPos to, Block block) {
        fill(singleplayerContext, from, to, block.defaultBlockState());
    }

    static void fill(TestSingleplayerContext singleplayerContext, BlockPos from, BlockPos to, BlockState state) {
        singleplayerContext.getServer().runOnServer(_ -> fill(singleplayerContext.getConnection().getServerLevel(), from, to, state));
    }

    // Same block update behavior as /fill and /setblock
    static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        record UpdatedPosition(BlockPos pos, BlockState oldState) {
        }

        List<UpdatedPosition> updatedPositions = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            BlockState oldState = level.getBlockState(pos);
            if (level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS)) {
                updatedPositions.add(new UpdatedPosition(pos.immutable(), oldState));
            }
        }
        for (UpdatedPosition updatedPosition : updatedPositions) {
            level.updateNeighboursOnBlockSet(updatedPosition.pos, updatedPosition.oldState);
        }
    }

    static <T extends Entity> T summon(TestSingleplayerContext singleplayerContext, EntityType<T> type, Vec3 pos) {
        return singleplayerContext.getServer().computeOnServer(_ -> {
            ServerLevel level = singleplayerContext.getConnection().getServerLevel();
            T entity = Objects.requireNonNull(type.create(level, EntitySpawnReason.COMMAND), () -> "Failed to create " + type);
            entity.snapTo(pos, entity.getYRot(), entity.getXRot());
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(entity.blockPosition()), EntitySpawnReason.COMMAND, null);
                mob.setNoAi(true);
                mob.setSilent(true);
            }
            if (!level.tryAddFreshEntityWithPassengers(entity)) {
                throw new IllegalStateException("Failed to add " + entity + " to the level");
            }
            return entity;
        });
    }

    static void teleport(TestSingleplayerContext singleplayerContext, Vec3 pos) {
        singleplayerContext.getServer().runOnServer(_ -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            teleport(player.level(), player, pos, player.getYRot(), player.getXRot());
        });
    }

    static void teleport(TestSingleplayerContext singleplayerContext, ResourceKey<Level> dimension, Vec3 pos) {
        singleplayerContext.getServer().runOnServer(server -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            teleport(Objects.requireNonNull(server.getLevel(dimension)), player, pos, player.getYRot(), player.getXRot());
        });
    }

    static void rotate(TestSingleplayerContext singleplayerContext, float yRot, float xRot) {
        singleplayerContext.getServer().runOnServer(_ -> {
            ServerPlayer player = singleplayerContext.getConnection().getServerPlayer();
            teleport(player.level(), player, player.position(), yRot, xRot);
        });
    }

    static void teleport(ServerLevel level, Entity entity, Vec3 pos, float yRot, float xRot) {
        if (!entity.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), Mth.wrapDegrees(yRot), Mth.wrapDegrees(xRot), true)) {
            throw new IllegalStateException("Failed to teleport " + entity);
        }
    }

    static void addEffect(TestSingleplayerContext singleplayerContext, Holder<MobEffect> effect, int amplifier) {
        singleplayerContext.getServer().runOnServer(_ ->
            singleplayerContext.getConnection().getServerPlayer().addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier))
        );
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0));

        try (TestSingleplayerContext singleplayerContext = context.worldBuilder().adjustSettings(settings -> {
            settings.setDifficulty(Difficulty.NORMAL);
            settings.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 0, null);
            settings.getGameRules().set(GameRules.IMMEDIATE_RESPAWN, true, null);
            settings.getGameRules().set(GameRules.FALL_DAMAGE, false, null);
            settings.getGameRules().set(GameRules.DROWNING_DAMAGE, false, null);
            settings.getGameRules().set(GameRules.SPAWN_MONSTERS, false, null);
            // The client always assumes entity cramming happens at 24 entities, but we don't want the player to die from it
            settings.getGameRules().set(GameRules.MAX_ENTITY_CRAMMING, 0, null);
        }).create()) {
            context.runOnClient(_ -> {
                if (!registeredRngCallListener) {
                    registeredRngCallListener = true;
                    PlayerRandCracker.RNG_CALLED_EVENT.register(event -> rngCalls.add(event.getType()));
                }
            });

            failedTests.clear();

            List<Method> testMethods = new ArrayList<>();
            boolean seenOnlyMe = false;

            for (Method method : TEST_CLASSES.stream().flatMap(clazz -> Arrays.stream(clazz.getDeclaredMethods())).toList()) {
                if (method.isAnnotationPresent(TestCase.class)) {
                    if (!Modifier.isStatic(method.getModifiers())) {
                        throw new IllegalStateException("Test methods must be static");
                    }

                    TestCase testCase = method.getAnnotation(TestCase.class);

                    if (seenOnlyMe) {
                        if (!testCase.onlyMe()) {
                            continue;
                        }
                    } else if (testCase.onlyMe()) {
                        seenOnlyMe = true;
                        testMethods.clear();
                    }

                    testMethods.add(method);
                }
            }

            for (Method method : testMethods) {
                Object[] args = new Object[method.getParameterCount()];
                Class<?>[] parameterTypes = method.getParameterTypes();
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> paramType = parameterTypes[i];
                    if (paramType == ClientGameTestContext.class) {
                        args[i] = context;
                    } else if (paramType == TestSingleplayerContext.class) {
                        args[i] = singleplayerContext;
                    } else {
                        throw new IllegalStateException("Illegal parameter type " + paramType + " in " + method);
                    }
                }
                try {
                    method.setAccessible(true);
                    method.invoke(null, args);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                } catch (InvocationTargetException e) {
                    throw sneakyThrow(e.getCause());
                }
            }

            context.runOnClient(_ -> clearProtocolVersionOverride());
        }

        if (!failedTests.isEmpty()) {
            throw new IllegalStateException("There were failing tests: " + failedTests);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    private static Method getCallingTest() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames ->
            frames.map(frame -> {
                Class<?> clazz = frame.getDeclaringClass();
                try {
                    return clazz.getDeclaredMethod(frame.getMethodName(), frame.getMethodType().parameterArray());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }).filter(method -> method.isAnnotationPresent(TestCase.class)).findFirst().orElseThrow()
        );
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TestCase {
        boolean onlyMe() default false;
    }
}
