package net.earthcomputer.clientcommands.script;

import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptUtils;
import net.earthcomputer.clientcommands.MathUtil;
import net.earthcomputer.clientcommands.interfaces.IMinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.container.Container;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.network.packet.PlayerMoveC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TagHelper;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;

import java.util.function.Predicate;

@SuppressWarnings("unused")
public class ScriptPlayer extends ScriptLivingEntity {

    ScriptPlayer() {
        super(getPlayer());
    }

    private static ClientPlayerEntity getPlayer() {
        return MinecraftClient.getInstance().player;
    }

    @Override
    ClientPlayerEntity getEntity() {
        return getPlayer();
    }

    public void snapTo(double x, double y, double z) {
        snapTo(x, y, z, false);
    }

    public void snapTo(double x, double y, double z, boolean sync) {
        double dx = x - getX();
        double dy = y - getY();
        double dz = z - getZ();
        if (dx * dx + dy * dy + dz * dz > 0.5 * 0.5)
            return;

        getPlayer().setPosition(x, y, z);

        if (sync)
            getPlayer().networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionOnly(x, y, z, getPlayer().onGround));
    }

    public void setYaw(float yaw) {
        getPlayer().yaw = yaw;
    }

    public void setPitch(float pitch) {
        getPlayer().pitch = pitch;
    }

    public void lookAt(double x, double y, double z) {
        ClientPlayerEntity player = getPlayer();
        double dx = x - player.x;
        double dy = y - (player.y + getEyeHeight());
        double dz = z - player.z;
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
        getPlayer().networkHandler.sendPacket(new PlayerMoveC2SPacket.LookOnly(getPlayer().yaw, getPlayer().pitch, getPlayer().onGround));
    }

    public int getSelectedSlot() {
        return getPlayer().inventory.selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        getPlayer().inventory.selectedSlot = MathHelper.clamp(slot, 0, 8);
    }

    public ScriptInventory getInventory() {
        return new ScriptInventory(getPlayer().playerContainer);
    }

    public ScriptInventory getOpenContainer() {
        Container container = getPlayer().container;
        if (container == getPlayer().playerContainer)
            return null;
        else
            return new ScriptInventory(getPlayer().container);
    }

    public void closeContainer() {
        if (getPlayer().container != getPlayer().playerContainer)
            getPlayer().closeContainer();
    }

    public boolean pick(Object itemStack) {
        Predicate<ItemStack> predicate;
        if (itemStack instanceof String) {
            Item item = Registry.ITEM.get(new Identifier((String) itemStack));
            predicate = stack -> stack.getItem() == item;
        } else if (itemStack instanceof JSObject && ((JSObject) itemStack).isFunction()) {
            JSObject jsObject = (JSObject) itemStack;
            predicate = stack -> {
                Object result = jsObject.call(null, ScriptUtil.fromNbt(stack.toTag(new CompoundTag())));
                return (Boolean) ScriptUtils.convert(result, Boolean.class);
            };
        } else {
            Tag nbt = ScriptUtil.toNbt(itemStack);
            if (!(nbt instanceof CompoundTag))
                throw new IllegalArgumentException(itemStack.toString());
            predicate = stack -> TagHelper.areTagsEqual(nbt, stack.toTag(new CompoundTag()), true);
        }

        PlayerInventory inv = getPlayer().inventory;
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
            ActionResult result = MinecraftClient.getInstance().interactionManager.interactItem(getPlayer(), getPlayer().world, hand);
            if (result == ActionResult.SUCCESS) {
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
        BlockPos pos = new BlockPos(x, y, z);
        Direction dir = null;
        if (side != null) {
            for (Direction d : Direction.values()) {
                if (d.name().equalsIgnoreCase(side)) {
                    dir = d;
                    break;
                }
            }
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        BlockState state = world.getBlockState(pos);
        if (state.isAir())
            return false;
        Vec3d origin = getPlayer().getCameraPosVec(0);
        Vec3d closestPos = MathUtil.getClosestVisiblePoint(world, pos, origin, getPlayer(), dir);
        if (closestPos == null)
            return false;
        if (origin.squaredDistanceTo(closestPos) < 6 * 6) {
            lookAt(closestPos.x, closestPos.y, closestPos.z);
            getPlayer().swingHand(Hand.MAIN_HAND);
            return MinecraftClient.getInstance().interactionManager.attackBlock(pos,
                    dir == null ? Direction.getFacing((float) (closestPos.x - origin.x), (float) (closestPos.y - origin.y), (float) (closestPos.z - origin.z)) : dir);
        }
        return false;
    }

    public boolean rightClick(int x, int y, int z) {
        return rightClick(x, y, z, null);
    }

    public boolean rightClick(int x, int y, int z, String side) {
        BlockPos pos = new BlockPos(x, y, z);
        Direction dir = null;
        if (side != null) {
            for (Direction d : Direction.values()) {
                if (d.name().equalsIgnoreCase(side)) {
                    dir = d;
                    break;
                }
            }
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        BlockState state = world.getBlockState(pos);
        if (state.isAir())
            return false;
        Vec3d origin = getPlayer().getCameraPosVec(0);
        Vec3d closestPos = MathUtil.getClosestVisiblePoint(world, pos, origin, getPlayer(), dir);
        if (closestPos == null)
            return false;
        if (origin.squaredDistanceTo(closestPos) < 6 * 6) {
            for (Hand hand : Hand.values()) {
                ActionResult result = MinecraftClient.getInstance().interactionManager.interactBlock(getPlayer(), world, hand,
                        new BlockHitResult(closestPos,
                                dir == null ? Direction.getFacing((float) (closestPos.x - origin.x), (float) (closestPos.y - origin.y), (float) (closestPos.z - origin.z)) : dir,
                                pos, false));
                if (result == ActionResult.SUCCESS) {
                    lookAt(closestPos.x, closestPos.y, closestPos.z);
                    getPlayer().swingHand(hand);
                    return true;
                }
                if (result == ActionResult.FAIL)
                    return false;
            }
        }
        return false;
    }

    public boolean leftClick(ScriptEntity entity) {
        if (getPlayer().squaredDistanceTo(entity.getEntity()) > 6 * 6)
            return false;

        lookAt(entity);
        getPlayer().swingHand(Hand.MAIN_HAND);
        MinecraftClient.getInstance().interactionManager.attackEntity(getPlayer(), entity.getEntity());
        return true;
    }

    public boolean rightClick(ScriptEntity entity) {
        if (getPlayer().squaredDistanceTo(entity.getEntity()) > 6 * 6)
            return false;

        for (Hand hand : Hand.values()) {
            ActionResult result = MinecraftClient.getInstance().interactionManager.interactEntity(getPlayer(), entity.getEntity(), hand);
            if (result == ActionResult.SUCCESS) {
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
        if (!getPlayer().isUsingItem())
            return false;
        boolean wasBlockingInput = ScriptManager.isCurrentScriptBlockingInput();
        ScriptManager.blockInput(true);
        do {
            ScriptManager.passTick();
        } while (getPlayer().isUsingItem());
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
        ScriptManager.blockInput(true);
        do {
            IMinecraftClient imc = (IMinecraftClient) MinecraftClient.getInstance();
            imc.resetAttackCooldown();
            imc.continueBreakingBlock();
            ScriptManager.passTick();
        } while (interactionManager.isBreakingBlock());
        ScriptManager.blockInput(wasBlockingInput);
        return true;
    }

    public void setPressingForward(boolean pressingForward) {
        ScriptManager.getScriptInput().pressingForward = pressingForward;
    }

    public boolean isPressingForward() {
        return ScriptManager.getScriptInput().pressingForward || getPlayer().input.pressingForward;
    }

    public void setPressingBack(boolean pressingBack) {
        ScriptManager.getScriptInput().pressingBack = pressingBack;
    }

    public boolean isPressingBack() {
        return ScriptManager.getScriptInput().pressingBack || getPlayer().input.pressingBack;
    }

    public void setPressingLeft(boolean pressingLeft) {
        ScriptManager.getScriptInput().pressingLeft = pressingLeft;
    }

    public boolean isPressingLeft() {
        return ScriptManager.getScriptInput().pressingLeft || getPlayer().input.pressingLeft;
    }

    public void setPressingRight(boolean pressingRight) {
        ScriptManager.getScriptInput().pressingRight = pressingRight;
    }

    public boolean isPressingRight() {
        return ScriptManager.getScriptInput().pressingRight || getPlayer().input.pressingRight;
    }

    public void setJumping(boolean jumping) {
        ScriptManager.getScriptInput().jumping = jumping;
    }

    public boolean isJumping() {
        return ScriptManager.getScriptInput().jumping || getPlayer().input.jumping;
    }

    public void setSneaking(boolean sneaking) {
        ScriptManager.getScriptInput().sneaking = sneaking;
    }

    public boolean isSneaking() {
        return ScriptManager.getScriptInput().sneaking || getPlayer().input.sneaking;
    }

    public void setSprinting(boolean sprinting) {
        ScriptManager.setSprinting(sprinting);
    }

    public boolean isSprinting() {
        return ScriptManager.isCurrentScriptSprinting() || MinecraftClient.getInstance().options.keySprint.isPressed();
    }

}
