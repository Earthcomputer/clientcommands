package net.earthcomputer.clientcommands.c2c;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.earthcomputer.clientcommands.c2c.packets.CoinflipC2CPackets;
import net.earthcomputer.clientcommands.c2c.packets.MessageC2CPacket;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class CCPacketHandler {

    private static final Object2IntMap<Class<? extends C2CPacket>> packetIds = Util.make(new Object2IntOpenHashMap<>(), map -> map.defaultReturnValue(-1));
    private static final List<Function<PacketByteBuf, ? extends C2CPacket>> packetFactories = new ArrayList<>();

    static {
        CCPacketHandler.register(MessageC2CPacket.class, MessageC2CPacket::new);
        CoinflipC2CPackets.register();
    }

    public static <P extends C2CPacket> void register(Class<P> packet, Function<PacketByteBuf, P> packetFactory) {
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
    public static C2CPacket createPacket(int id, PacketByteBuf buf) {
        Function<PacketByteBuf, ? extends C2CPacket> function = packetFactories.get(id);
        return function == null ? null : function.apply(buf);
    }
}
