package net.earthcomputer.clientcommands.command;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.stream.Collectors;

public class FakeCommandSource extends CommandSourceStack {
    public FakeCommandSource(LocalPlayer player) {
        super(new CommandSource() {
            @Override
            public void sendSystemMessage(Component component) {
                ClientCommandHelper.sendFeedback(component);
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return true;
            }
        }, player.position(), player.getRotationVector(), null, 314159265, player.getScoreboardName(), player.getName(), null, player);
    }

    @NotNull
    @Override
    public Collection<String> getOnlinePlayerNames() {
        return Minecraft.getInstance().getConnection().getOnlinePlayers()
                .stream().map(e -> e.getProfile().getName()).collect(Collectors.toList());
    }

    @NotNull
    @Override
    public RegistryAccess registryAccess() {
        return Minecraft.getInstance().getConnection().registryAccess();
    }
}
