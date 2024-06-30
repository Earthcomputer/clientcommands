package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.command.CDebugCommand.DebugScreenType.*;

public class CDebugCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cdebug")
            .executes(context -> execute(OVERLAY))
            .then(literal("overlay")
                .executes(context -> execute(OVERLAY)))
            .then(literal("fps")
                .executes(context -> execute(FPS)))
            .then(literal("network")
                .executes(context -> execute(NETWORK)))
            .then(literal("profiler")
                .executes(context -> execute(PROFILER))));
    }

    private static int execute(DebugScreenType type) {
        DebugScreenOverlay debugScreenOverlay = Minecraft.getInstance().getDebugOverlay();
        switch (type) {
            case OVERLAY -> debugScreenOverlay.toggleOverlay();
            case FPS -> debugScreenOverlay.toggleFpsCharts();
            case NETWORK -> debugScreenOverlay.toggleNetworkCharts();
            case PROFILER -> debugScreenOverlay.toggleProfilerChart();
        }
        return Command.SINGLE_SUCCESS;
    }
    
    enum DebugScreenType {
        OVERLAY,
        FPS,
        NETWORK,
        PROFILER
    }
}
