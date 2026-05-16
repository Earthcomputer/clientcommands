package net.earthcomputer.clientcommands.mixin.commands.relog;

import net.earthcomputer.clientcommands.interfaces.IDisconnectedScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

@Mixin(DisconnectedScreen.class)
public abstract class DisconnectedScreenMixin extends Screen implements IDisconnectedScreen {
    @Unique
    @Nullable
    private Long countdownEndTimestamp;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Override
    public void clientcommands_setCountdownMs(long ms) {
        countdownEndTimestamp = System.currentTimeMillis() + ms;
    }

    @Override
    public long clientcommands_getRemainingMs() {
        assert countdownEndTimestamp != null : "No known end timestamp";
        return countdownEndTimestamp - System.currentTimeMillis();
    }
}
