package net.earthcomputer.clientcommands.mixin;

import net.earthcomputer.clientcommands.interfaces.IFlaggedCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ClientCommandSource.class)
public class MixinClientCommandSource implements IFlaggedCommandSource {

    @Shadow
    @Final
    private ClientPlayNetworkHandler networkHandler;

    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private int flags;

    @Override
    public int getFlags() {
        return this.flags;
    }

    @Override
    public IFlaggedCommandSource withFlags(int flags) {
        MixinClientCommandSource source = (MixinClientCommandSource) (Object) new ClientCommandSource(this.networkHandler, this.client);
        source.flags = flags;

        return source;
    }

}
