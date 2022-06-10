package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class PermissionLevelCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cpermissionlevel")
            .executes(ctx -> getPermissionLevel(ctx.getSource())));
    }

    private static int getPermissionLevel(FabricClientCommandSource source) {
        int permissionLevel = ((IEntity) source.getPlayer()).callGetPermissionLevel();
        source.sendFeedback(Text.translatable("commands.cpermissionlevel.success", permissionLevel));

        return permissionLevel;
    }

}
