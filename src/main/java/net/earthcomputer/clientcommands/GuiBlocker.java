package net.earthcomputer.clientcommands;

import net.minecraft.client.gui.Screen;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class GuiBlocker {

    private static List<GuiBlocker> blockers = new ArrayList<>();
    public static boolean onOpenGui(Screen screen) {
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
    public static void tick() {
        Iterator<GuiBlocker> itr = blockers.iterator();
        while (itr.hasNext()) {
            GuiBlocker blocker = itr.next();
            blocker.timeoutCounter--;
            if (blocker.timeoutCounter <= 0)
                itr.remove();
        }
    }
    public static void addBlocker(GuiBlocker blocker) {
        blockers.add(blocker);
    }

    private int timeoutCounter = 100;

    /**
     * Return false to not open the GUI
     */
    public abstract boolean accept(Screen screen);

}
