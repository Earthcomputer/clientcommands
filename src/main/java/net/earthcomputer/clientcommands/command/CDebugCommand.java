package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CDebugCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cdebug")
                .executes((context -> execute("overlay")))
                .then(literal("fps").executes(context -> execute("fps")))
                .then(literal("network").executes(context -> execute("network")))
                .then(literal("profiler").executes(context -> execute("profiler")))
        );
    }

    private static int execute(String type) {
        DebugScreenOverlay debugScreenOverlay = Minecraft.getInstance().getDebugOverlay();
        switch (type) {
            case "overlay":
                debugScreenOverlay.toggleOverlay(); break;
            case "fps":
                debugScreenOverlay.toggleFpsCharts(); break;
            case "network":
                debugScreenOverlay.toggleNetworkCharts(); break;
            case "profiler":
                debugScreenOverlay.toggleProfilerChart(); break;
        }
        return Command.SINGLE_SUCCESS;
    }
}
