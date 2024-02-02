package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ShrugCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cshrug")
            .executes(ctx -> shrug(ctx.getSource())));
    }

    private static int shrug(FabricClientCommandSource source) {
        ClientPacketListener networkHandler = Minecraft.getInstance().getConnection();
        if (networkHandler != null) {
            networkHandler.sendChat("¯\\_(ツ)_/¯");
        }
        return Command.SINGLE_SUCCESS;
    }

}
