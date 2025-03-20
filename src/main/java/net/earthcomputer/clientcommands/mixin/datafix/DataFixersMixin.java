package net.earthcomputer.clientcommands.mixin.datafix;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import net.minecraft.util.datafix.DataFixers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(DataFixers.class)
public class DataFixersMixin {
    @ModifyExpressionValue(
        method = "addFixers",
        slice = @Slice(from = @At(value = "CONSTANT", args = "intValue=4187")),
        at = @At(value = "INVOKE", target = "Lcom/mojang/datafixers/DataFixerBuilder;addSchema(ILjava/util/function/BiFunction;)Lcom/mojang/datafixers/schemas/Schema;", ordinal = 0, remap = false))
    private static Schema addFixers(Schema original, @Local(argsOnly = true) DataFixerBuilder builder) {
        // TODO
        // builder.addFixer(new WaypointAddVisibilityIfNotPresentFix(original, true, ChatFormatting.WHITE.getColor()));
        return original;
    }
}
