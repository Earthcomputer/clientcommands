package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class MapCommand {
    private static final SimpleCommandExceptionType NO_HELD_MAP_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cmap.noHeld"));


    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cmap")
            .then(literal("export")
                .executes(ctx -> exportMap(ctx.getSource(), 1))
                .then(argument("upscale", integer(1, 16))
                    .executes(ctx -> exportMap(ctx.getSource(), getInteger(ctx, "upscale")))
                )
            )
        );
    }

    private static MapState getMapState(ClientPlayerEntity player) throws CommandSyntaxException {
        ItemStack map;

        // detect if map in hand
        if (player.getMainHandStack().getItem() instanceof FilledMapItem) {
            map = player.getMainHandStack();
        } else if (player.getOffHandStack().getItem() instanceof FilledMapItem) {
            map = player.getOffHandStack();
        } else {
            throw NO_HELD_MAP_EXCEPTION.create();
        }

        Integer mapId = FilledMapItem.getMapId(map);
        MapState mapState = FilledMapItem.getMapState(mapId, player.world);
        if (mapState == null) {
            throw NO_HELD_MAP_EXCEPTION.create();
        }
        return mapState;
    }


    private static int exportMap(FabricClientCommandSource source, int upscale) throws CommandSyntaxException {
        MapState mapState = getMapState(source.getPlayer());
        try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, 128 * upscale, 128 * upscale, false)) {
            for (int i = 0; i < 128 * upscale; i+= upscale) {
                for (int j = 0; j < 128 * upscale; j+= upscale) {
                    int x = i / upscale;
                    int y = j / upscale;
                    int color = MapColor.getRenderColor(mapState.colors[x + y * 128]);
                    for (int k = 0; k < upscale; k++) {
                        for (int l = 0; l < upscale; l++) {
                            image.setColor(i + k, j + l, color);
                        }
                    }
                }
            }
            File screenshotDir = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
            screenshotDir.mkdirs();
            File file = ScreenshotRecorder.getScreenshotFilename(screenshotDir);

            image.writeTo(file);

            source.sendFeedback(Text.translatable("commands.cmap.success", file.getName()).styled(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file.getAbsolutePath()))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        return Command.SINGLE_SUCCESS;
    }

}
