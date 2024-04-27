package net.earthcomputer.clientcommands;

import net.earthcomputer.clientcommands.event.MoreScreenEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class GuiBlocker {
    static {
        MoreScreenEvents.BEFORE_ADD.register(GuiBlocker::onOpenGui);
        ClientTickEvents.START_CLIENT_TICK.register(GuiBlocker::tick);
    }

    private static final List<GuiBlocker> blockers = new ArrayList<>();

    private static boolean onOpenGui(@Nullable Screen screen) {
        boolean shouldOpen = true;
        Iterator<GuiBlocker> itr = blockers.iterator();
        while (itr.hasNext()) {
            GuiBlocker blocker = itr.next();
            boolean pass = blocker.accept(screen);
            if (!pass) {
                shouldOpen = false;
                itr.remove();
            }
        }
        return shouldOpen;
    }

    private static void tick(Minecraft mc) {
        Iterator<GuiBlocker> itr = blockers.iterator();
        while (itr.hasNext()) {
            GuiBlocker blocker = itr.next();
            blocker.timeoutCounter--;
            if (blocker.timeoutCounter <= 0) {
                itr.remove();
            }
        }
    }

    public static void addBlocker(GuiBlocker blocker) {
        blockers.add(blocker);
    }

    private int timeoutCounter = 100;

    /**
     * Return false to not open the GUI
     */
    public abstract boolean accept(@Nullable Screen screen);

}
