package net.earthcomputer.clientcommands.features;

import net.minecraft.client.gui.screens.Screen;

public abstract class TwoPlayerGame<T extends Screen> {
    public abstract T createScreen();
}
