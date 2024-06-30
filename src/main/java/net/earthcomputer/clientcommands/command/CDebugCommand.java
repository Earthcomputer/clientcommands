package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import static dev.xpple.clientarguments.arguments.CEnumArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class CDebugCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cdebug")
            .executes(context -> executeOverlay())
            .then(argument("type", enumArg(DebugScreenType.class))
                .executes(CDebugCommand::execute))
        );
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        DebugScreenOverlay debugScreenOverlay = Minecraft.getInstance().getDebugOverlay();
        DebugScreenType type = getEnum(ctx, "type");
        switch (type) {
            case OVERLAY -> debugScreenOverlay.toggleOverlay();
            case FPS -> debugScreenOverlay.toggleFpsCharts();
            case NETWORK -> debugScreenOverlay.toggleNetworkCharts();
            case PROFILER -> debugScreenOverlay.toggleProfilerChart();
        }
        return Command.SINGLE_SUCCESS;
    }
    
    private static int executeOverlay() {
        Minecraft.getInstance().getDebugOverlay().toggleOverlay();
        return Command.SINGLE_SUCCESS;
    }
    
    public enum DebugScreenType implements StringRepresentable {
        OVERLAY("overlay"),
        FPS("fps"),
        NETWORK("network"),
        PROFILER("profiler");
        
        private final String name;
        
        DebugScreenType(String name) {
            this.name = name;
        }
        
        public @NotNull String getSerializedName() {
            return this.name;
        }
    }
}
