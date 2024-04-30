package net.earthcomputer.clientcommands.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public final class MoreScreenEvents {
    public static final Event<BeforeAdd> BEFORE_ADD = EventFactory.createArrayBacked(BeforeAdd.class, listeners -> screen -> {
        boolean accept = true;
        for (BeforeAdd listener : listeners) {
            accept &= listener.beforeScreenAdd(screen);
        }
        return accept;
    });

    @FunctionalInterface
    public interface BeforeAdd {
        boolean beforeScreenAdd(@Nullable Screen screen);
    }
}
