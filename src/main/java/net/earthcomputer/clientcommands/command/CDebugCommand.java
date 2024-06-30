package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CDebugCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cdebug")
            .executes(context -> execute("overlay"))
            .then(literal("overlay")
                .executes(context -> execute("overlay")))
            .then(literal("fps")
                .executes(context -> execute("fps")))
            .then(literal("network")
                .executes(context -> execute("network")))
            .then(literal("profiler")
                .executes(context -> execute("profiler"))));
    }

    private static int execute(String type) {
        DebugScreenOverlay debugScreenOverlay = Minecraft.getInstance().getDebugOverlay();
        switch (type) {
            case "overlay" -> debugScreenOverlay.toggleOverlay();
            case "fps" -> debugScreenOverlay.toggleFpsCharts();
            case "network" -> debugScreenOverlay.toggleNetworkCharts();
            case "profiler" -> debugScreenOverlay.toggleProfilerChart();
        }
        return Command.SINGLE_SUCCESS;
    }
}
