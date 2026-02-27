package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.lwjgl.glfw.GLFW;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class WindowSizeCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cwindowsize")
            .then(argument("width", integer(0))
                .then(argument("height", integer(0))
                    .executes(ctx -> setWindowSize(ctx.getSource(), getInteger(ctx, "width"), getInteger(ctx, "height"))))));
    }

    private static int setWindowSize(FabricClientCommandSource source, int width, int height) {
        Window window = source.getClient().getWindow();
        long handle = window.handle();

        int oldX = window.getX();
        int oldY = window.getY();
        int oldWidth = window.getWidth();
        int oldHeight = window.getHeight();

        int centerX = oldX + oldWidth / 2;
        int centerY = oldY + oldHeight / 2;

        GLFW.glfwSetWindowSize(handle, width, height);

        int newX = centerX - width / 2;
        int newY = centerY - height / 2;

        GLFW.glfwSetWindowPos(handle, newX, newY);
        return Command.SINGLE_SUCCESS;
    }
}
