package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;

import net.earthcomputer.clientcommands.interfaces.IEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.minecraft.server.command.CommandManager.*;

public class PermissionLevelCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cpermissionlevel");

        dispatcher.register(literal("cpermissionlevel")
            .executes(ctx -> getPermissionLevel(ctx.getSource())));
    }

    private static int getPermissionLevel(ServerCommandSource source) {
        assert MinecraftClient.getInstance().player != null;

        int permissionLevel = ((IEntity) MinecraftClient.getInstance().player).callGetPermissionLevel();
        sendFeedback(new TranslatableText("commands.cpermissionlevel.success", permissionLevel));

        return permissionLevel;
    }

}
