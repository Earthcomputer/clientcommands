package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.util.StringRepresentable;

import static dev.xpple.clientarguments.arguments.CEnumArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class CDebugCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cdebug")
            .executes(ctx -> execute(ctx.getSource(), DebugScreenType.OVERLAY))
            .then(argument("type", enumArg(DebugScreenType.class))
                .executes(ctx -> execute(ctx.getSource(), getEnum(ctx, "type"))))
        );
    }

    private static int execute(FabricClientCommandSource source, DebugScreenType type) {
        DebugScreenOverlay debugScreenOverlay = source.getClient().getDebugOverlay();
        switch (type) {
            case OVERLAY -> source.getClient().debugEntries.toggleDebugOverlay();
            case FPS -> debugScreenOverlay.toggleFpsCharts();
            case NETWORK -> debugScreenOverlay.toggleNetworkCharts();
            case PROFILER -> debugScreenOverlay.toggleProfilerChart();
        }
        return Command.SINGLE_SUCCESS;
    }

    private enum DebugScreenType implements StringRepresentable {
        OVERLAY("overlay"),
        FPS("fps"),
        NETWORK("network"),
        PROFILER("profiler");
        
        private final String name;
        
        DebugScreenType(String name) {
            this.name = name;
        }
        
        public String getSerializedName() {
            return this.name;
        }
    }
}
