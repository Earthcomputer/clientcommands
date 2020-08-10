package net.earthcomputer.clientcommands.script;

import com.google.common.collect.Lists;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ScriptItemStack {

    private ItemStack stack;

    ScriptItemStack(ItemStack stack) {
        this.stack = stack;
    }

    public Object getStack() {
        return ScriptUtil.fromNbtCompound(stack.toTag(new CompoundTag()));
    }

    public float getMiningSpeed(String block) {
        return getMiningSpeed(ScriptBlockState.defaultState(block));
    }

    public float getMiningSpeed(ScriptBlockState block) {
        return stack.getMiningSpeedMultiplier(block.state);
    }

    public boolean isEffectiveOn(String block) {
        return isEffectiveOn(ScriptBlockState.defaultState(block));
    }

    public boolean isEffectiveOn(ScriptBlockState block) {
        return stack.isEffectiveOn(block.state);
    }

    public int getMaxCount() {
        return stack.getMaxCount();
    }

    public int getMaxDamage() {
        return stack.getMaxDamage();
    }

    public boolean isIsFood() {
        return stack.isFood();
    }

    public int getHungerRestored() {
        FoodComponent food = stack.getItem().getFoodComponent();
        return food == null ? 0 : food.getHunger();
    }

    public float getSaturationRestored() {
        FoodComponent food = stack.getItem().getFoodComponent();
        return food == null ? 0 : food.getSaturationModifier();
    }

    public boolean isIsMeat() {
        FoodComponent food = stack.getItem().getFoodComponent();
        return food != null && food.isMeat();
    }

    public boolean isAlwaysEdible() {
        FoodComponent food = stack.getItem().getFoodComponent();
        return food != null && food.isAlwaysEdible();
    }

    public boolean isIsSnack() {
        FoodComponent food = stack.getItem().getFoodComponent();
        return food != null && food.isSnack();
    }

    public List<String> getTags() {
        //noinspection ConstantConditions
        return Lists.transform(
                new ArrayList<>(MinecraftClient.getInstance().getNetworkHandler().getTagManager().getItems().getTagsFor(stack.getItem())),
                ScriptUtil::simplifyIdentifier);
    }

}
