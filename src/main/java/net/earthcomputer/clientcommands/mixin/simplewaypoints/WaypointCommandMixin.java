package net.earthcomputer.clientcommands.mixin.simplewaypoints;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import dev.xpple.simplewaypoints.commands.WaypointCommand;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

@Mixin(WaypointCommand.class)
public class WaypointCommandMixin {
    @Inject(method = "register", at = @At("TAIL"), remap = false)
    private static void registerWaypointCommandAlias(CommandDispatcher<FabricClientCommandSource> dispatcher, CallbackInfo ci) {
        CommandNode<FabricClientCommandSource> configRoot = dispatcher.getRoot().getChild("sw:waypoint");
        dispatcher.register(literal("cwaypoint").redirect(configRoot));
    }
}
