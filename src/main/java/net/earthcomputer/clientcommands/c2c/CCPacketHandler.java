package net.earthcomputer.clientcommands.c2c;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.earthcomputer.clientcommands.c2c.packets.*;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CCPacketHandler {

    private static final Object2IntMap<Class<? extends C2CPacket>> packetIds = Util.make(new Object2IntOpenHashMap<>(), map -> map.defaultReturnValue(-1));
    private static final List<Function<StringBuf, ? extends C2CPacket>> packetFactories = new ArrayList<>();

    static {
        CCPacketHandler.register(MessageC2CPacket.class, MessageC2CPacket::new);
        CCPacketHandler.register(SnakeInviteC2CPacket.class, SnakeInviteC2CPacket::new);
        CCPacketHandler.register(SnakeJoinC2CPacket.class, SnakeJoinC2CPacket::new);
        CCPacketHandler.register(SnakeAddPlayersC2CPacket.class, SnakeAddPlayersC2CPacket::new);
        CCPacketHandler.register(SnakeBodyC2CPacket.class, SnakeBodyC2CPacket::new);
        CCPacketHandler.register(SnakeRemovePlayerC2CPacket.class, SnakeRemovePlayerC2CPacket::new);
        CCPacketHandler.register(SnakeSyncAppleC2CPacket.class, SnakeSyncAppleC2CPacket::new);
    }

    public static <P extends C2CPacket> void register(Class<P> packet, Function<StringBuf, P> packetFactory) {
        int id = packetFactories.size();
        int i = packetIds.put(packet, id);
        if (i != -1) {
            String string = "Packet " + packet + " is already registered to ID " + i;
            throw new IllegalArgumentException(string);
        }
        packetFactories.add(packetFactory);
    }

    @Nullable
    public static <P extends C2CPacket> Integer getId(Class<P> packet) {
        int id = packetIds.getInt(packet);
        return id == -1 ? null : id;
    }

    @Nullable
    public static C2CPacket createPacket(int id, StringBuf buf) {
        Function<StringBuf, ? extends C2CPacket> function = packetFactories.get(id);
        return function == null ? null : function.apply(buf);
    }
}
