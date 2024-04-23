package net.earthcomputer.clientcommands.features;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CommandExecutionCustomPayload(String command) implements CustomPacketPayload {
    public static final ResourceLocation ID = new ResourceLocation("clientcommands", "command_execution");
    public static final StreamCodec<FriendlyByteBuf, CommandExecutionCustomPayload> CODEC = CustomPacketPayload.codec(CommandExecutionCustomPayload::write, CommandExecutionCustomPayload::new);
    public static final CustomPacketPayload.Type<CommandExecutionCustomPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    private CommandExecutionCustomPayload(FriendlyByteBuf buf) {
        this(buf.readUtf());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.command);
    }

    @Override
    public CustomPacketPayload.Type<CommandExecutionCustomPayload> type() {
        return TYPE;
    }
}
