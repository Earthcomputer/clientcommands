package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.permissions.PermissionSet;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class PermissionLevelCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cpermissionlevel")
            .executes(ctx -> getPermissionLevel(ctx.getSource())));
    }

    private static int getPermissionLevel(FabricClientCommandSource source) {
        int permissionLevel = getPermissionLevel(source.getPlayer().permissions());
        source.sendFeedback(Component.translatable("commands.cpermissionlevel.success", permissionLevel));

        return permissionLevel;
    }

    private static int getPermissionLevel(PermissionSet permissions) {
        PermissionLevel[] levels = PermissionLevel.values();

        for (int i = levels.length - 1; i >= 0; i--) {
            if (permissions.hasPermission(new Permission.HasCommandLevel(levels[i]))) {
                return levels[i].id();
            }
        }

        return PermissionLevel.ALL.id();
    }
}
