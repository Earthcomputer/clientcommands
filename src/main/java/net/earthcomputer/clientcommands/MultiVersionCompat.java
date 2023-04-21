package net.earthcomputer.clientcommands;

import com.google.common.collect.Iterables;
import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.Item;
import net.minecraft.network.ClientConnection;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

public abstract sealed class MultiVersionCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int V1_13_2 = 404;
    public static final int V1_14 = 477;
    public static final int V1_14_2 = 485;
    public static final int V1_15 = 573;
    public static final int V1_15_2 = 578;
    public static final int V1_17 = 755;
    public static final int V1_18 = 757;

    public abstract int getProtocolVersion();

    public abstract String getProtocolName();

    public boolean doesItemExist(Item item) {
        return true;
    }

    public static final MultiVersionCompat INSTANCE = Util.make(() -> {
        try {
            FabricLoader loader = FabricLoader.getInstance();
            if (loader.isModLoaded("multiconnect")) {
                return new Multiconnect();
            } else if (loader.isModLoaded("viafabric")) {
                return new ViaFabric();
            } else if (loader.isModLoaded("viafabricplus")) {
                return new ViaFabricPlus();
            } else {
                return new None();
            }
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Could not load proper MultiVersionCompat", e);
            return new None();
        }
    });

    private static final class None extends MultiVersionCompat {
        @Override
        public int getProtocolVersion() {
            return SharedConstants.getProtocolVersion();
        }

        @Override
        public String getProtocolName() {
            return SharedConstants.getGameVersion().getName();
        }
    }

    private static final class Multiconnect extends MultiVersionCompat {
        private final Object api;
        private final Method getProtocolVersion;
        private final Method byProtocolVersion;
        private final Method protocolGetName;

        private Multiconnect() throws ReflectiveOperationException {
            api = Class.forName("net.earthcomputer.multiconnect.api.MultiConnectAPI").getMethod("instance").invoke(null);
            getProtocolVersion = api.getClass().getMethod("getProtocolVersion");
            byProtocolVersion = api.getClass().getMethod("byProtocolVersion", int.class);
            protocolGetName = byProtocolVersion.getReturnType().getMethod("getName");
        }

        @Override
        public int getProtocolVersion() {
            try {
                return (Integer) getProtocolVersion.invoke(api);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getProtocolName() {
            try {
                return (String) protocolGetName.invoke(byProtocolVersion.invoke(api, getProtocolVersion.invoke(api)));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static abstract sealed class AbstractViaVersion extends MultiVersionCompat {
        protected final Class<?> protocolVersion;
        private final Method getVersion;
        private final Method getIncludedVersions;

        private AbstractViaVersion() throws ReflectiveOperationException {
            protocolVersion = Class.forName("com.viaversion.viaversion.api.protocol.version.ProtocolVersion");
            getVersion = protocolVersion.getMethod("getVersion");
            getIncludedVersions = protocolVersion.getMethod("getIncludedVersions");
        }

        protected abstract Object getCurrentVersion() throws ReflectiveOperationException;

        @Override
        public int getProtocolVersion() {
            try {
                return (Integer) getVersion.invoke(getCurrentVersion());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public String getProtocolName() {
            Set<String> includedVersions;
            try {
                includedVersions = (Set<String>) getIncludedVersions.invoke(getCurrentVersion());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            // the returned set is sorted, so the last version is the latest one
            return Iterables.getLast(includedVersions);
        }
    }

    private static final class ViaFabric extends AbstractViaVersion {
        private final Class<?> fabricDecodeHandler;
        private final Field channel;
        private final Method getInfo;
        private final Method getProtocolInfo;
        private final Method getServerProtocolVersion;
        private final Method isRegistered;
        private final Method getProtocol;

        private ViaFabric() throws ReflectiveOperationException {
            fabricDecodeHandler = Class.forName("com.viaversion.fabric.common.handler.FabricDecodeHandler");
            getInfo = fabricDecodeHandler.getMethod("getInfo");
            getProtocolInfo = getInfo.getReturnType().getMethod("getProtocolInfo");
            getServerProtocolVersion = getProtocolInfo.getReturnType().getMethod("getServerProtocolVersion");
            isRegistered = protocolVersion.getMethod("isRegistered", int.class);
            getProtocol = protocolVersion.getMethod("getProtocol", int.class);

            Field channelField = null;
            for (Field field : ClientConnection.class.getDeclaredFields()) {
                if (field.getType() == Channel.class) {
                    channelField = field;
                    channelField.setAccessible(true);
                    break;
                }
            }
            if (channelField == null) {
                throw new NoSuchFieldException("Could not find channel field in ClientConnection");
            }
            channel = channelField;
        }

        @Override
        public int getProtocolVersion() {
            try {
                return doGetProtocolVersion();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        protected Object getCurrentVersion() throws ReflectiveOperationException {
            int protocolVersion = doGetProtocolVersion();

            if (!(Boolean) isRegistered.invoke(null, protocolVersion)) {
                protocolVersion = SharedConstants.getProtocolVersion();
            }
            return getProtocol.invoke(null, protocolVersion);
        }

        private int doGetProtocolVersion() throws ReflectiveOperationException {
            int protocolVersion = SharedConstants.getProtocolVersion();

            // https://github.com/ViaVersion/ViaFabric/blob/fda8d39147d46c80698d204538ede790f02589f6/viafabric-mc18/src/main/java/com/viaversion/fabric/mc18/mixin/debug/client/MixinDebugHud.java
            ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
            if (networkHandler != null) {
                Channel channel = (Channel) this.channel.get(networkHandler.getConnection());
                ChannelHandler viaDecoder = channel.pipeline().get("via-decoder");
                if (fabricDecodeHandler.isInstance(viaDecoder)) {
                    Object user = getInfo.invoke(viaDecoder);
                    Object protocol = getProtocolInfo.invoke(user);
                    if (protocol != null) {
                        protocolVersion = (Integer) getServerProtocolVersion.invoke(protocol);
                    }
                }
            }

            return protocolVersion;
        }
    }

    private static final class ViaFabricPlus extends AbstractViaVersion {
        private final Field itemReleaseVersionMappingsInstance;
        private final Method getTargetVersion;
        private final Method itemReleaseVersionMappingsContains;

        private ViaFabricPlus() throws ReflectiveOperationException {
            Class<?> protocolHack = Class.forName("de.florianmichael.viafabricplus.protocolhack.ProtocolHack");
            Class<?> itemReleaseVersionMappings = Class.forName("de.florianmichael.viafabricplus.mappings.ItemReleaseVersionMappings");
            itemReleaseVersionMappingsInstance = itemReleaseVersionMappings.getField("INSTANCE");
            getTargetVersion = protocolHack.getMethod("getTargetVersion");
            itemReleaseVersionMappingsContains = itemReleaseVersionMappings.getMethod("contains", Item.class, getTargetVersion.getReturnType());
        }

        @Override
        protected Object getCurrentVersion() throws ReflectiveOperationException {
            return getTargetVersion.invoke(null);
        }

        @Override
        public boolean doesItemExist(Item item) {
            try {
                Object instance = itemReleaseVersionMappingsInstance.get(null);
                return (Boolean) itemReleaseVersionMappingsContains.invoke(instance, item, getCurrentVersion());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
