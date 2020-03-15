package net.earthcomputer.clientcommands.script;

import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptUtils;
import net.earthcomputer.clientcommands.interfaces.ISlot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.container.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public class ScriptInventory {

    private final Container container;

    ScriptInventory(Container container) {
        this.container = container;
    }

    public String getType() {
        if (container instanceof PlayerContainer)
            return "player";
        if (container instanceof CreativeInventoryScreen.CreativeContainer)
            return "creative";
        if (container instanceof HorseContainer)
            return "horse";
        ContainerType<?> type = container.getType();
        if (type == null)
            return null;
        return ScriptUtil.simplifyIdentifier(Registry.CONTAINER.getId(type));
    }

    /**
     * If this is a player container, then slots are the hotbar, main inventory, armor, offhand, crafting result and crafting grid,
     * in that order.
     * Otherwise, they are the container items in order.
     */
    public List<Object> getItems() {
        List<Object> ret = new ArrayList<>();
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (container == player.playerContainer) {
            for (int i = 0; i < player.inventory.getInvSize(); i++) {
                ret.add(ScriptUtil.fromNbt(player.inventory.getInvStack(i).toTag(new CompoundTag())));
            }
            // crafting grid
            for (int i = 0; i < 5; i++)
                ret.add(ScriptUtil.fromNbt(container.slotList.get(i).getStack().toTag(new CompoundTag())));
        } else {
            for (Slot slot : container.slotList) {
                if (slot.inventory != player.inventory) {
                    ret.add(ScriptUtil.fromNbt(slot.getStack().toTag(new CompoundTag())));
                }
            }
        }
        return ret;
    }

    public void click(Integer slot) {
        click(slot, null);
    }

    public void click(Integer slot, JSObject options) {
        String typeStr = options == null || !options.hasMember("type") ? null : ScriptUtil.asString(options.getMember("type"));
        SlotActionType type = typeStr == null ? SlotActionType.PICKUP :
                Arrays.stream(SlotActionType.values()).filter(it -> it.name().equalsIgnoreCase(typeStr)).findAny().orElse(SlotActionType.PICKUP);

        int mouseButton;
        if (type == SlotActionType.SWAP) {
            if (!options.hasMember("hotbarSlot"))
                throw new IllegalArgumentException("When the click type is swap, the options must also contain the hotbar slot to swap with");
            mouseButton = MathHelper.clamp(ScriptUtil.asNumber(options.getMember("hotbarSlot")).intValue(), 0, 8);
        } else if (type == SlotActionType.QUICK_CRAFT) {
            if (!options.hasMember("quickCraftStage"))
                throw new IllegalArgumentException("When the click type is quick_craft, the options must also contain the quick craft stage");
            mouseButton = ScriptUtil.asNumber(options.getMember("quickCraftStage")).intValue();
        } else {
            if (options == null || !options.hasMember("rightClick"))
                mouseButton = 0;
            else
                mouseButton = ScriptUtil.asBoolean(options.getMember("rightClick")) ? 1 : 0;
        }

        int slotId = -1;
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (slot == null) {
            slotId = -999;
        } else if (container == player.playerContainer) {
            if (player.container == player.playerContainer && slot >= player.inventory.getInvSize() && slot < player.inventory.getInvSize() + 5) {
                slotId = slot - player.inventory.getInvSize();
            } else {
                for (Slot s : player.container.slotList) {
                    if (s.inventory == player.inventory && ((ISlot) s).getInvSlot() == slot) {
                        slotId = s.id;
                        break;
                    }
                }
            }
        } else if (container == player.container) {
            int curSlotId = 0;
            for (Slot s : container.slotList) {
                if (s.inventory != player.inventory) {
                    if (curSlotId == slot) {
                        slotId = curSlotId;
                        break;
                    }
                    curSlotId++;
                }
            }
        }

        if (slotId == -1) {
            throw new IllegalArgumentException("Slot not in open container");
        }

        MinecraftClient.getInstance().interactionManager.clickSlot(player.container.syncId, slotId, mouseButton, type, player);
    }

    @Override
    public int hashCode() {
        return container.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ScriptInventory)) return false;
        return container.equals(((ScriptInventory) o).container);
    }
}
