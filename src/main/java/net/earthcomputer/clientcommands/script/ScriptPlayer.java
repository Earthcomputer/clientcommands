package net.earthcomputer.clientcommands.script;

import com.google.common.collect.ImmutableSet;
import jdk.nashorn.api.scripting.JSObject;
import net.earthcomputer.clientcommands.MathUtil;
import net.earthcomputer.clientcommands.features.PathfindingHints;
import net.earthcomputer.clientcommands.features.PlayerPathfinder;
import net.earthcomputer.clientcommands.features.Relogger;
import net.earthcomputer.clientcommands.interfaces.IBlockChangeListener;
import net.earthcomputer.clientcommands.interfaces.IMinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.AbstractRecipeScreenHandler;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class ScriptPlayer extends ScriptLivingEntity {

    ScriptPlayer() {
        super(MinecraftClient.getInstance().player);
    }

    @Override
    Entity getNullableEntity() {
        return MinecraftClient.getInstance().player;
    }

    @Override
    ClientPlayerEntity getEntity() {
        return (ClientPlayerEntity) super.getEntity();
    }

    public boolean snapTo(double x, double y, double z) {
        return snapTo(x, y, z, false);
    }

    public boolean snapTo(double x, double y, double z, boolean sync) {
        double dx = x - getX();
        double dy = y - getY();
        double dz = z - getZ();
        if (dx * dx + dy * dy + dz * dz > 0.5 * 0.5)
            return false;

        getEntity().setPos(x, y, z);

        if (sync)
            getEntity().networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionOnly(x, y, z, getEntity().isOnGround()));

        return true;
    }

    public boolean moveTo(double x, double z) {
        return moveTo(x, z, true);
    }

    public boolean moveTo(double x, double z, boolean smart) {
        if (getEntity().squaredDistanceTo(x, getY(), z) < 0.01) {
            snapTo(x, getY(), z);
            return true;
        }

        lookAt(x, getY() + getEyeHeight(), z);
        boolean wasBlockingInput = ScriptManager.isCurrentScriptBlockingInput();
        ScriptManager.blockInput(true);
        boolean wasPressingForward = ScriptManager.getScriptInput().pressingForward;
        ScriptManager.getScriptInput().pressingForward = true;

        double lastDistanceSq = getEntity().squaredDistanceTo(x, getY(), z);
        int tickCounter = 0;
        boolean successful = true;

        do {
            if (smart) {
                double dx = x - getX();
                double dz = z - getZ();
                double n = Math.sqrt(dx * dx + dz * dz);
                dx /= n;
                dz /= n;
                BlockPos pos = new BlockPos(MathHelper.floor(getX() + dx), MathHelper.floor(getY()), MathHelper.floor(getZ() + dz));
                World world = getEntity().world;
                if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                        && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
                        && world.getBlockState(pos.up(2)).getCollisionShape(world, pos.up(2)).isEmpty()) {
                    BlockPos aboveHead = getEntity().getBlockPos().up(2);
                    if (world.getBlockState(aboveHead).getCollisionShape(world, aboveHead).isEmpty()) {
                        if (getEntity().squaredDistanceTo(x, getY(), z) > 1
                                || getEntity().getBoundingBox().offset(x - getX(), 0, z - getZ()).intersects(new Box(pos))) {
                            boolean wasJumping = ScriptManager.getScriptInput().jumping;
                            ScriptManager.getScriptInput().jumping = true;
                            ScriptManager.passTick();
                            ScriptManager.getScriptInput().jumping = wasJumping;
                        }
                    }
                }
            }
            lookAt(x, getY() + getEyeHeight(), z);
            ScriptManager.passTick();

            tickCounter++;
            if (tickCounter % 20 == 0) {
                double distanceSq = getEntity().squaredDistanceTo(x, getY(), z);
                if (distanceSq >= lastDistanceSq) {
                    successful = false;
                    break;
                }
                lastDistanceSq = distanceSq;
            }
        } while (getEntity().squaredDistanceTo(x, getY(), z) > 0.25 * 0.25);
        snapTo(x, getY(), z);

        ScriptManager.getScriptInput().pressingForward = wasPressingForward;
        ScriptManager.blockInput(wasBlockingInput);

        return successful;
    }

    public boolean pathTo(double x, double y, double z) {
        return pathTo(x, y, z, null);
    }

    public boolean pathTo(double x, double y, double z, JSObject hints) {
        BlockPos pos = new BlockPos(x, y, z);
        return pathTo0(() -> pos, hints, false);
    }

    public boolean pathTo(Object thing) {
        return pathTo(thing, null);
    }

    public boolean pathTo(Object thing, JSObject hints) {
        if (thing instanceof ScriptEntity) {
            Entity entity = ((ScriptEntity) thing).getEntity();
            return pathTo0(entity::getBlockPos, hints, true);
        } else {
            JSObject func = ScriptUtil.asFunction(thing);
            return pathTo0(() -> {
                JSObject posObj = ScriptUtil.asObject(func.call(null));
                double x = ScriptUtil.asNumber(posObj.getMember("x")).doubleValue();
                double y = ScriptUtil.asNumber(posObj.getMember("y")).doubleValue();
                double z = ScriptUtil.asNumber(posObj.getMember("z")).doubleValue();
                return new BlockPos(x, y, z);
            }, hints, true);
        }
    }

    private boolean pathTo0(Supplier<BlockPos> target, JSObject hints, boolean movingTarget) {
        JSObject nodeTypeFunction = hints != null && hints.hasMember("nodeTypeFunction") ? ScriptUtil.asFunction(hints.getMember("nodeTypeFunction")) : null;
        JSObject penaltyFunction = hints != null && hints.hasMember("penaltyFunction") ? ScriptUtil.asFunction(hints.getMember("penaltyFunction")) : null;
        Float followRange = hints != null && hints.hasMember("followRange") ? ScriptUtil.asNumber(hints.getMember("followRange")).floatValue() : null;
        int reachDistance = hints != null && hints.hasMember("reachDistance") ? ScriptUtil.asNumber(hints.getMember("reachDistance")).intValue() : 0;
        Float maxPathLength = hints != null && hints.hasMember("maxPathLength") ? ScriptUtil.asNumber(hints.getMember("maxPathLength")).floatValue() : null;

        BlockPos[] targetPos = {target.get()};

        PathfindingHints javaHints = new PathfindingHints() {
            @Override
            public PathNodeType getNodeType(BlockView world, BlockPos pos) {
                if (nodeTypeFunction == null)
                    return null;

                Object typeObj = nodeTypeFunction.call(null, pos.getX(), pos.getY(), pos.getZ());
                if (typeObj == null)
                    return null;

                String typeName = ScriptUtil.asString(typeObj);
                try {
                    return PathNodeType.valueOf(typeName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown path node type \"" + typeName + "\"");
                }
            }

            @Override
            public float getPathfindingPenalty(PathNodeType type) {
                if (penaltyFunction == null)
                    return type.getDefaultPenalty();

                String typeName = type.name().toLowerCase(Locale.ROOT);
                Object penaltyObj = penaltyFunction.call(null, typeName);

                if (penaltyObj == null)
                    return type.getDefaultPenalty();

                return ScriptUtil.asNumber(penaltyObj).floatValue();
            }

            @Override
            public float getFollowRange() {
                if (followRange != null)
                    return followRange;
                return (float) Math.sqrt(getEntity().squaredDistanceTo(targetPos[0].getX() + 0.5, targetPos[0].getY() + 0.5, targetPos[0].getZ() + 0.5)) * 2;
            }

            @Override
            public int getReachDistance() {
                return reachDistance;
            }

            @Override
            public float getMaxPathLength() {
                if (maxPathLength != null)
                    return maxPathLength;
                return (float) Math.sqrt(getEntity().squaredDistanceTo(targetPos[0].getX() + 0.5, targetPos[0].getY() + 0.5, targetPos[0].getZ() + 0.5)) * 2;
            }
        };

        Path[] path = {PlayerPathfinder.findPathToAny(ImmutableSet.of(targetPos[0]), javaHints)};
        boolean[] needsRecalc = {false};
        //noinspection Convert2Lambda - need a new instance every time
        IBlockChangeListener blockChangeListener = new IBlockChangeListener() {
            @Override
            public void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {
                if (path[0] == null || path[0].isFinished() || path[0].getLength() == 0)
                    return;
                PathNode end = path[0].getEnd();
                Vec3d halfway = new Vec3d((end.x + getEntity().getX()) / 2, (end.y + getEntity().getY()) / 2, (end.z + getEntity().getZ()) / 2);
                if (pos.isWithinDistance(halfway, path[0].getLength() - path[0].getCurrentNodeIndex())) {
                    needsRecalc[0] = true;
                }
            }
        };
        IBlockChangeListener.LISTENERS.add(blockChangeListener);

        try {
            while (path[0] != null && !path[0].isFinished()) {
                Vec3i currentPosition = path[0].getNode(path[0].getCurrentNodeIndex()).getPos();
                if (!moveTo(currentPosition.getX() + 0.5, currentPosition.getZ() + 0.5))
                    return false;
                path[0].setCurrentNodeIndex(path[0].getCurrentNodeIndex() + 1);
                if (movingTarget || needsRecalc[0]) {
                    BlockPos newTargetPos = target.get();
                    if (!newTargetPos.equals(targetPos[0]) || needsRecalc[0]) {
                        targetPos[0] = newTargetPos;
                        needsRecalc[0] = false;
                        path[0] = PlayerPathfinder.findPathToAny(ImmutableSet.of(targetPos[0]), javaHints);
                    }
                }
            }
        } finally {
            IBlockChangeListener.LISTENERS.remove(blockChangeListener);
        }

        return path[0] != null && path[0].getEnd() != null && path[0].getEnd().getPos().equals(targetPos[0]);
    }

    public void setYaw(float yaw) {
        getEntity().yaw = yaw;
    }

    public void setPitch(float pitch) {
        getEntity().pitch = pitch;
    }

    public void lookAt(double x, double y, double z) {
        ClientPlayerEntity player = getEntity();
        double dx = x - player.getX();
        double dy = y - (player.getY() + getEyeHeight());
        double dz = z - player.getZ();
        double dh = Math.sqrt(dx * dx + dz * dz);
        player.yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        player.pitch = (float) -Math.toDegrees(Math.atan2(dy, dh));
    }

    public void lookAt(ScriptEntity entity) {
        if (entity instanceof ScriptLivingEntity) {
            double eyeHeight = ((ScriptLivingEntity) entity).getEyeHeight();
            lookAt(entity.getX(), entity.getY() + eyeHeight, entity.getZ());
        } else {
            lookAt(entity.getX(), entity.getY(), entity.getZ());
        }
    }

    public void syncRotation() {
        getEntity().networkHandler.sendPacket(new PlayerMoveC2SPacket.LookOnly(getEntity().yaw, getEntity().pitch, getEntity().isOnGround()));
    }

    public int getSelectedSlot() {
        return getEntity().inventory.selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        getEntity().inventory.selectedSlot = MathHelper.clamp(slot, 0, 8);
    }

    public ScriptInventory getInventory() {
        return new ScriptInventory(getEntity().playerScreenHandler);
    }

    public ScriptInventory getCurrentContainer() {
        ScreenHandler container = getEntity().currentScreenHandler;
        if (container == getEntity().playerScreenHandler)
            return null;
        else
            return new ScriptInventory(getEntity().currentScreenHandler);
    }

    public boolean openContainer(int x, int y, int z, Object containerType) {
        return openContainer0(() -> rightClick(x, y, z), containerType);
    }

    public boolean openContainer(ScriptEntity entity, Object containerType) {
        return openContainer0(() -> rightClick(entity), containerType);
    }

    private boolean openContainer0(BooleanSupplier rightClickFunction, Object containerType) {
        Predicate<String> containerTypePredicate;
        if (ScriptUtil.isFunction(containerType)) {
            JSObject containerTypeFunc = ScriptUtil.asFunction(containerType);
            containerTypePredicate = type -> ScriptUtil.asBoolean(containerTypeFunc.call(null, type));
        } else {
            String containerTypeName = ScriptUtil.asString(containerType);
            containerTypePredicate = containerTypeName::equals;
        }

        if (!rightClickFunction.getAsBoolean())
            return false;

        int timeout = 0;
        while (getCurrentContainer() == null || !containerTypePredicate.test(getCurrentContainer().getType())) {
            ScriptManager.passTick();
            timeout++;
            if (timeout > 100)
                return false;
        }

        return true;
    }

    public void closeContainer() {
        if (getEntity().currentScreenHandler != getEntity().playerScreenHandler) {
            MinecraftClient.getInstance().execute(getEntity()::closeHandledScreen);
            ScriptManager.passTick();
        }
    }

    public int craft(Object result, int count, String[] pattern, JSObject ingredients) {
        // Convert js input to something we can handle in Java

        Predicate<ItemStack> resultPredicate = ScriptUtil.asItemStackPredicate(result);

        int patternWidth = -1;
        Map<Character, Predicate<ItemStack>> ingredientPredicates = new HashMap<>();
        for (String row : pattern) {
            if (patternWidth == -1)
                patternWidth = row.length();
            else if (row.length() != patternWidth)
                throw new IllegalArgumentException("Inconsistent pattern width");
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ') {
                    ingredientPredicates.computeIfAbsent(c, k -> {
                        String s = String.valueOf(k);
                        if (!ingredients.hasMember(s))
                            throw new IllegalArgumentException("Character '" + k + "' in pattern not found in ingredients");
                        return ScriptUtil.asItemStackPredicate(ingredients.getMember(s));
                    });
                }
            }
        }
        if (ingredientPredicates.isEmpty())
            throw new IllegalArgumentException("Empty pattern");

        // Check if we are actually in a container with a crafting grid, or return if the recipe is too big for the grid
        if (!(getEntity().currentScreenHandler instanceof CraftingScreenHandler) && !(getEntity().currentScreenHandler instanceof PlayerScreenHandler)) {
            return 0;
        }
        AbstractRecipeScreenHandler<?> container = (AbstractRecipeScreenHandler<?>) getEntity().currentScreenHandler;
        int resultSlotIndex = container.getCraftingResultSlotIndex();
        int craftingSlotCount = container.getCraftingSlotCount();
        if (pattern.length > container.getCraftingHeight())
            return 0;
        if (patternWidth > container.getCraftingWidth())
            return 0;

        ClientPlayerInteractionManager interactionManager = MinecraftClient.getInstance().interactionManager;
        assert interactionManager != null;

        emptyCraftingGrid(container, resultSlotIndex, craftingSlotCount, interactionManager);

        // Craft the items
        int craftsNeeded = count;
        craftLoop: while (craftsNeeded > 0) {
            Map<Character, List<Integer>> ingredientsNeeded = new HashMap<>();
            for (int x = 0; x < patternWidth; x++) {
                for (int y = 0; y < pattern.length; y++) {
                    char c = pattern[y].charAt(x);
                    if (c != ' ') {
                        int slotId = 1 + x + container.getCraftingWidth() * y;
                        Slot slot = container.getSlot(slotId);
                        if (!ingredientPredicates.get(c).test(slot.getStack())) {
                            if (slot.hasStack()) {
                                if (!getEntity().inventory.getCursorStack().isEmpty()) {
                                    craftInsertIntoPlayerInv(container, interactionManager);
                                }
                                interactionManager.clickSlot(container.syncId, slotId, 0, SlotActionType.QUICK_MOVE, getEntity());
                                if (slot.hasStack()) {
                                    interactionManager.clickSlot(container.syncId, slotId, 1, SlotActionType.THROW, getEntity());
                                }
                            }
                            ingredientsNeeded.computeIfAbsent(c, k -> new ArrayList<>()).add(slotId);
                        }
                    }
                }
            }

            if (ingredientsNeeded.isEmpty()) {
                int timeout = 0;
                Slot resultSlot = container.getSlot(resultSlotIndex);
                while (!resultPredicate.test(resultSlot.getStack())) {
                    ScriptManager.passTick();
                    if (timeout++ > 100) {
                        break craftLoop;
                    }
                }
                ItemStack cursorStack = getEntity().inventory.getCursorStack();
                if (!ScreenHandler.canStacksCombine(getEntity().inventory.getCursorStack(), resultSlot.getStack())
                        || cursorStack.getCount() + resultSlot.getStack().getCount() > cursorStack.getMaxCount()) {
                    craftInsertIntoPlayerInv(container, interactionManager);
                }
                interactionManager.clickSlot(container.syncId, resultSlotIndex, 0, SlotActionType.PICKUP, getEntity());
                craftsNeeded--;
            } else {
                if (!getEntity().inventory.getCursorStack().isEmpty()) {
                    craftInsertIntoPlayerInv(container, interactionManager);
                }
                boolean craftingGridStartedEmpty = true;
                for (int slotId = resultSlotIndex + 1; slotId < resultSlotIndex + craftingSlotCount; slotId++) {
                    if (container.getSlot(slotId).hasStack()) {
                        craftingGridStartedEmpty = false;
                        break;
                    }
                }
                ingredientFillLoop: for (Map.Entry<Character, List<Integer>> ingredient : ingredientsNeeded.entrySet()) {
                    Predicate<ItemStack> ingredientPredicate = ingredientPredicates.get(ingredient.getKey());
                    List<Integer> destSlots = ingredient.getValue();
                    while (!destSlots.isEmpty()) {
                        boolean foundIngredient = false;
                        for (Slot slot : container.slots) {
                            if (isPlayerInvSlot(container, slot)) {
                                if (ingredientPredicate.test(slot.getStack())) {
                                    List<Integer> slotsToPlace = slot.getStack().getCount() < destSlots.size() ? destSlots.subList(0, slot.getStack().getCount()) : destSlots;
                                    interactionManager.clickSlot(container.syncId, slot.id, 0, SlotActionType.PICKUP, getEntity());
                                    if (slotsToPlace.size() == 1) {
                                        interactionManager.clickSlot(container.syncId, slotsToPlace.get(0), 0, SlotActionType.PICKUP, getEntity());
                                    } else {
                                        interactionManager.clickSlot(container.syncId, slotsToPlace.get(0), ScreenHandler.packQuickCraftData(0, 0), SlotActionType.QUICK_CRAFT, getEntity());
                                        for (int destSlot : slotsToPlace) {
                                            interactionManager.clickSlot(container.syncId, destSlot, ScreenHandler.packQuickCraftData(1, 0), SlotActionType.QUICK_CRAFT, getEntity());
                                        }
                                        interactionManager.clickSlot(container.syncId, slotsToPlace.get(slotsToPlace.size() - 1), ScreenHandler.packQuickCraftData(2, 0), SlotActionType.QUICK_CRAFT, getEntity());
                                    }
                                    foundIngredient = !slotsToPlace.isEmpty();
                                    slotsToPlace.clear();
                                    break;
                                }
                            }
                        }
                        if (!foundIngredient) {
                            if (craftingGridStartedEmpty) {
                                break craftLoop;
                            } else {
                                emptyCraftingGrid(container, resultSlotIndex, craftingSlotCount, interactionManager);
                                break ingredientFillLoop;
                            }
                        }
                    }
                }
            }
        }

        emptyCraftingGrid(container, resultSlotIndex, craftingSlotCount, interactionManager);

        return count - craftsNeeded;
    }

    private void emptyCraftingGrid(AbstractRecipeScreenHandler<?> container, int resultSlotIndex, int craftingSlotCount, ClientPlayerInteractionManager interactionManager) {
        // Get rid of all items in the cursor + the crafting grid
        if (!getEntity().inventory.getCursorStack().isEmpty()) {
            craftInsertIntoPlayerInv(container, interactionManager);
        }
        for (int slotIndex = resultSlotIndex + 1; slotIndex < resultSlotIndex + craftingSlotCount; slotIndex++) {
            Slot slot = container.getSlot(slotIndex);
            if (slot.hasStack()) {
                interactionManager.clickSlot(container.syncId, slotIndex, 0, SlotActionType.QUICK_MOVE, getEntity());
                if (slot.hasStack()) {
                    interactionManager.clickSlot(container.syncId, slotIndex, 1, SlotActionType.THROW, getEntity());
                }
            }
        }
    }

    private void craftInsertIntoPlayerInv(AbstractRecipeScreenHandler<?> container, ClientPlayerInteractionManager interactionManager) {
        ItemStack cursorStack = getEntity().inventory.getCursorStack();
        int cursorStackCount = cursorStack.getCount();

        for (int playerSlotIndex = 0; playerSlotIndex < container.slots.size(); playerSlotIndex++) {
            Slot slot = container.getSlot(playerSlotIndex);
            if (isPlayerInvSlot(container, slot)) {
                if (ScreenHandler.canStacksCombine(cursorStack, slot.getStack())) {
                    int maxCount = Math.min(cursorStack.getMaxCount(), slot.getMaxItemCount(cursorStack));
                    if (slot.getStack().getCount() < maxCount) {
                        interactionManager.clickSlot(container.syncId, playerSlotIndex, 0, SlotActionType.PICKUP, getEntity());
                        cursorStackCount -= maxCount - slot.getStack().getCount();
                        if (cursorStackCount <= 0)
                            return;
                    }
                }
            }
        }
        for (int playerSlotIndex = 0; playerSlotIndex < container.slots.size(); playerSlotIndex++) {
            Slot slot = container.getSlot(playerSlotIndex);
            if (isPlayerInvSlot(container, slot)) {
                if (!slot.hasStack()) {
                    int maxCount = Math.min(cursorStack.getMaxCount(), slot.getMaxItemCount(cursorStack));
                    interactionManager.clickSlot(container.syncId, playerSlotIndex, 0, SlotActionType.PICKUP, getEntity());
                    cursorStackCount -= maxCount;
                    if (cursorStackCount <= 0)
                        return;
                }
            }
        }
        interactionManager.clickSlot(container.syncId, -999, 0, SlotActionType.PICKUP, getEntity());
    }

    private boolean isPlayerInvSlot(AbstractRecipeScreenHandler<?> container, Slot slot) {
        return slot.inventory instanceof PlayerInventory
                && (slot.id < container.getCraftingResultSlotIndex() || slot.id >= container.getCraftingResultSlotIndex() + container.getCraftingSlotCount());
    }

    public boolean pick(Object itemStack) {
        Predicate<ItemStack> predicate = ScriptUtil.asItemStackPredicate(itemStack);

        PlayerInventory inv = getEntity().inventory;
        int slot;
        for (slot = 0; slot < inv.main.size(); slot++) {
            if (predicate.test(inv.main.get(slot)))
                break;
        }
        if (slot == inv.main.size())
            return false;

        if (PlayerInventory.isValidHotbarIndex(slot)) {
            setSelectedSlot(slot);
        } else {
            int hotbarSlot = getSelectedSlot();
            do {
                if (inv.main.get(hotbarSlot).isEmpty())
                    break;
                hotbarSlot = (hotbarSlot + 1) % 9;
            } while (hotbarSlot != getSelectedSlot());
            setSelectedSlot(hotbarSlot);
            MinecraftClient.getInstance().interactionManager.pickFromInventory(slot);
        }
        return true;
    }

    public boolean rightClick() {
        for (Hand hand : Hand.values()) {
            ActionResult result = MinecraftClient.getInstance().interactionManager.interactItem(getEntity(), getEntity().world, hand);
            if (result.isAccepted()) {
                MinecraftClient.getInstance().gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
                return true;
            }
            if (result == ActionResult.FAIL)
                return false;
        }
        return false;
    }

    public boolean leftClick(int x, int y, int z) {
        return leftClick(x, y, z, null);
    }

    public boolean leftClick(int x, int y, int z, String side) {
        Vec3d closestPos = ScriptWorld.getClosestVisiblePoint0(x, y, z, side, true);
        if (closestPos == null)
            return false;
        lookAt(closestPos.x, closestPos.y, closestPos.z);
        getEntity().swingHand(Hand.MAIN_HAND);
        Vec3d origin = getEntity().getCameraPosVec(0);
        Direction dir = ScriptUtil.getDirectionFromString(side);
        return MinecraftClient.getInstance().interactionManager.attackBlock(new BlockPos(x, y, z),
                dir == null ? Direction.getFacing((float) (closestPos.x - origin.x), (float) (closestPos.y - origin.y), (float) (closestPos.z - origin.z)) : dir);
    }

    public boolean rightClick(int x, int y, int z) {
        return rightClick(x, y, z, null);
    }

    public boolean rightClick(int x, int y, int z, String side) {
        Vec3d closestPos = ScriptWorld.getClosestVisiblePoint0(x, y, z, side, true);
        if (closestPos == null)
            return false;
        lookAt(closestPos.x, closestPos.y, closestPos.z);
        Vec3d origin = getEntity().getCameraPosVec(0);
        Direction dir = ScriptUtil.getDirectionFromString(side);
        for (Hand hand : Hand.values()) {
            ActionResult result = MinecraftClient.getInstance().interactionManager.interactBlock(getEntity(), MinecraftClient.getInstance().world, hand,
                    new BlockHitResult(closestPos,
                            dir == null ? Direction.getFacing((float) (closestPos.x - origin.x), (float) (closestPos.y - origin.y), (float) (closestPos.z - origin.z)) : dir,
                            new BlockPos(x, y, z), false));
            if (result.shouldSwingHand()) {
                getEntity().swingHand(hand);
                return true;
            }
            if (result == ActionResult.FAIL)
                return false;
        }
        return false;
    }

    public boolean leftClick(ScriptEntity entity) {
        if (getEntity().squaredDistanceTo(entity.getEntity()) > 6 * 6)
            return false;

        lookAt(entity);
        getEntity().swingHand(Hand.MAIN_HAND);
        MinecraftClient.getInstance().interactionManager.attackEntity(getEntity(), entity.getEntity());
        return true;
    }

    public boolean rightClick(ScriptEntity entity) {
        if (getEntity().squaredDistanceTo(entity.getEntity()) > 6 * 6)
            return false;

        for (Hand hand : Hand.values()) {
            ActionResult result = MinecraftClient.getInstance().interactionManager.interactEntity(getEntity(), entity.getEntity(), hand);
            if (result.isAccepted()) {
                lookAt(entity);
                return true;
            }
            if (result == ActionResult.FAIL)
                return false;
        }
        return false;
    }

    public void blockInput() {
        ScriptManager.blockInput(true);
    }

    public void unblockInput() {
        ScriptManager.blockInput(false);
    }

    public boolean longUseItem() {
        if (!rightClick())
            return false;
        if (!getEntity().isUsingItem())
            return false;
        boolean wasBlockingInput = ScriptManager.isCurrentScriptBlockingInput();
        ScriptManager.blockInput(true);
        do {
            ScriptManager.passTick();
        } while (getEntity().isUsingItem());
        ScriptManager.blockInput(wasBlockingInput);
        return true;
    }

    public boolean longMineBlock(int x, int y, int z) {
        if (!leftClick(x, y, z))
            return false;
        ClientPlayerInteractionManager interactionManager = MinecraftClient.getInstance().interactionManager;
        if (!interactionManager.isBreakingBlock())
            return false;
        boolean wasBlockingInput = ScriptManager.isCurrentScriptBlockingInput();
        boolean successful = true;
        ScriptManager.blockInput(true);
        BlockPos pos = new BlockPos(x, y, z);
        do {
            HitResult hitResult = MinecraftClient.getInstance().crosshairTarget;
            if (hitResult.getType() != HitResult.Type.BLOCK || !((BlockHitResult) hitResult).getBlockPos().equals(pos)) {
                Vec3d closestPos = MathUtil.getClosestVisiblePoint(MinecraftClient.getInstance().world, pos, getEntity().getCameraPosVec(0), getEntity());
                if (closestPos == null) {
                    successful = false;
                    break;
                }
                lookAt(closestPos.x, closestPos.y, closestPos.z);
            }
            IMinecraftClient imc = (IMinecraftClient) MinecraftClient.getInstance();
            imc.resetAttackCooldown();
            imc.continueBreakingBlock();
            ScriptManager.passTick();
        } while (interactionManager.isBreakingBlock());
        ScriptManager.blockInput(wasBlockingInput);
        return successful;
    }

    public void setPressingForward(boolean pressingForward) {
        ScriptManager.getScriptInput().pressingForward = pressingForward;
    }

    public boolean isPressingForward() {
        return ScriptManager.getScriptInput().pressingForward || getEntity().input.pressingForward;
    }

    public void setPressingBack(boolean pressingBack) {
        ScriptManager.getScriptInput().pressingBack = pressingBack;
    }

    public boolean isPressingBack() {
        return ScriptManager.getScriptInput().pressingBack || getEntity().input.pressingBack;
    }

    public void setPressingLeft(boolean pressingLeft) {
        ScriptManager.getScriptInput().pressingLeft = pressingLeft;
    }

    public boolean isPressingLeft() {
        return ScriptManager.getScriptInput().pressingLeft || getEntity().input.pressingLeft;
    }

    public void setPressingRight(boolean pressingRight) {
        ScriptManager.getScriptInput().pressingRight = pressingRight;
    }

    public boolean isPressingRight() {
        return ScriptManager.getScriptInput().pressingRight || getEntity().input.pressingRight;
    }

    public void setJumping(boolean jumping) {
        ScriptManager.getScriptInput().jumping = jumping;
    }

    public boolean isJumping() {
        return ScriptManager.getScriptInput().jumping || getEntity().input.jumping;
    }

    public void setSneaking(boolean sneaking) {
        ScriptManager.getScriptInput().sneaking = sneaking;
    }

    public boolean isSneaking() {
        return ScriptManager.getScriptInput().sneaking || getEntity().input.sneaking;
    }

    public void setSprinting(boolean sprinting) {
        ScriptManager.setSprinting(sprinting);
    }

    public boolean isSprinting() {
        return ScriptManager.isCurrentThreadSprinting() || MinecraftClient.getInstance().options.keySprint.isPressed();
    }

    public void disconnect() {
        MinecraftClient.getInstance().send(Relogger::disconnect);
        ScriptManager.passTick();
    }

    public boolean relog() {
        boolean[] ret = new boolean[1];
        MinecraftClient.getInstance().send(() -> ret[0] = Relogger.relog());
        ScriptManager.passTick();
        return ret[0];
    }

}
