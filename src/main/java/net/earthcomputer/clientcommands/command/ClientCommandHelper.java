package net.earthcomputer.clientcommands.command;

import com.demonwav.mcdev.annotations.Translatable;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.interfaces.IClientSuggestionsProvider;
import net.earthcomputer.clientcommands.server.ClientCommandsServer;
import net.earthcomputer.clientcommands.util.BuildInfo;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

import java.net.URI;

public class ClientCommandHelper {
    private static final URI WHY_URI = URI.create("https://github.com/Earthcomputer/clientcommands/blob/" + BuildInfo.COMMIT_HASH + "/docs/server_installation.md");
    private static final SimpleCommandExceptionType MUST_BE_INSTALLED_EXCEPTION = new SimpleCommandExceptionType(
        Component.translatable(
            "commands.client.mustBeInstalledOnServer",
            Component.translatable("commands.client.mustBeInstalledOnServer.why")
                .withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(WHY_URI)))
        )
    );

    public static void requirePrivileges() throws CommandSyntaxException {
        // Greetings, dear forker! I know there's nothing I can do to stop you nuking this method, and that's okay -
        // the license allows you to. However, I would prefer if you at least used a Mixin to target this method via a
        // different mod rather than forking my mod, that way I still get Modrinth revenue from downloads.

        if (ClientPlayNetworking.canSend(ClientCommandsServer.OptInPayload.TYPE)) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
            return;
        }

        throw MUST_BE_INSTALLED_EXCEPTION.create();
    }

    public static <T> T getFlag(CommandContext<FabricClientCommandSource> ctx, Flag<T> flag) {
        return getFlag(Flag.getActualSource(ctx), flag);
    }

    public static <T> T getFlag(FabricClientCommandSource source, Flag<T> flag) {
        return ((IClientSuggestionsProvider) source).clientcommands_getFlag(flag);
    }

    public static <T> FabricClientCommandSource withFlag(FabricClientCommandSource source, Flag<T> flag, T value) {
        return (FabricClientCommandSource) ((IClientSuggestionsProvider) source).clientcommands_withFlag(flag, value);
    }

    public static void sendError(Component error) {
        sendFeedback(Component.literal("").append(error).withStyle(ChatFormatting.RED));
    }

    public static void sendHelp(Component help) {
        sendFeedback(Component.literal("").append(help).withStyle(ChatFormatting.AQUA));
    }

    public static void sendFeedback(@Translatable String message, Object... args) {
        sendFeedback(Component.translatable(message, args));
    }

    public static void sendFeedback(Component message) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
    }

    public static void sendRequiresRestart() {
        sendFeedback(Component.translatable("commands.client.requiresRestart").withStyle(ChatFormatting.YELLOW));
    }

    public static void addOverlayMessage(Component message, int time) {
        Gui gui = Minecraft.getInstance().gui;
        gui.hud.setOverlayMessage(message, false);
        gui.hud.overlayMessageTime = time;
    }
}
