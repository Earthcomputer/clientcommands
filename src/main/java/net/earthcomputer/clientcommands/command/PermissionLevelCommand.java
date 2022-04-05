package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class PermissionLevelCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cpermissionlevel")
            .executes(ctx -> getPermissionLevel(ctx.getSource())));
    }

    private static int getPermissionLevel(FabricClientCommandSource source) {
        assert MinecraftClient.getInstance().player != null;

        int permissionLevel = ((IEntity) MinecraftClient.getInstance().player).callGetPermissionLevel();
        sendFeedback(new TranslatableText("commands.cpermissionlevel.success", permissionLevel));

        return permissionLevel;
    }

}
