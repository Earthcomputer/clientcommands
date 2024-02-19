package net.earthcomputer.clientcommands.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.response.ProfileSearchResultsResponse;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.util.ByteBufferTypeAdapter;
import com.mojang.util.InstantTypeAdapter;
import com.mojang.util.UUIDTypeAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.EncoderException;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.io.function.IOBiConsumer;
import org.apache.commons.io.function.IORunnable;
import org.apache.commons.io.function.IOStream;
import org.apache.commons.io.function.Uncheck;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.security.PublicKey;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * @author Gaming32
 */
public class PacketDumper {
    public static void dumpPacket(Packet<?> packet, JsonWriter writer) throws IOException {
        writer.beginArray();
        try {
            packet.write(new PacketDumpByteBuf(writer));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        writer.endArray();
    }

    private static class PacketDumpByteBuf extends FriendlyByteBuf {
        private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .registerTypeHierarchyAdapter(ByteBuffer.class, new ByteBufferTypeAdapter().nullSafe())
            .registerTypeAdapter(GameProfile.class, new GameProfile.Serializer())
            .registerTypeAdapter(PropertyMap.class, new PropertyMap.Serializer())
            .registerTypeAdapter(ProfileSearchResultsResponse.class, new ProfileSearchResultsResponse.Serializer())
            .create();
        private static final DateFormat ISO_8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");

        private final JsonWriter writer;

        public PacketDumpByteBuf(JsonWriter writer) {
            super(Unpooled.buffer(0, 0)); // Uses singleton EmptyByteBuf
            this.writer = writer;
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> @NotNull PacketDumpByteBuf writeWithCodec(DynamicOps<Tag> ops, Codec<T> codec, T value) {
            return dump("withCodec", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                writer.name("encodedNbt").value(Util.getOrThrow(
                    codec.encodeStart(ops, value),
                    message -> new EncoderException("Failed to encode: " + message + " " + value)
                ).toString());
                writer.name("encodedJson");
                GSON.toJson(Util.getOrThrow(
                    codec.encodeStart(JsonOps.INSTANCE, value),
                    message -> new EncoderException("Failed to encode: " + message + " " + value)
                ), writer);
            });
        }

        @Override
        public <T> void writeJsonWithCodec(Codec<T> codec, T value) {
            dump("jsonWithCodec", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                writer.name("encodedJson");
                GSON.toJson(Util.getOrThrow(
                    codec.encodeStart(JsonOps.INSTANCE, value),
                    message -> new EncoderException("Failed to encode: " + message + " " + value)
                ), writer);
            });
        }

        @Override
        public <T> void writeId(IdMap<T> idMap, T value) {
            dump("id", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                if (idMap instanceof Registry<T> registry) {
                    writer.name("registry").value(registry.key().location().toString());
                    writer.name("valueKey").value(Objects.toString(registry.getKey(value)));
                }
                writer.name("id").value(idMap.getId(value));
            });
        }

        @Override
        public <T> void writeId(IdMap<Holder<T>> idMap, Holder<T> value, Writer<T> directWriter) {
            dump("idHolder", () -> {
                writer.name("kind").value(value.kind().name());
                value.unwrap().ifLeft(key -> Uncheck.run(() -> {
                    writer.name("referenceKey").value(key.location().toString());
                    writer.name("id").value(idMap.getId(value));
                })).ifRight(directValue -> Uncheck.run(() -> {
                    writer.name("directValue");
                    dumpValue(directValue, directWriter);
                }));
            });
        }

        @Override
        public <T> void writeCollection(Collection<T> collection, Writer<T> elementWriter) {
            dump("collection", () -> {
                writer.name("size").value(collection.size());
                writer.name("elements").beginArray();
                for (final T element : collection) {
                    dumpValue(element, elementWriter);
                }
                writer.endArray();
            });
        }

        @Override
        public void writeIntIdList(IntList intIdList) {
            dump("intIdList", () -> {
                writer.name("size").value(intIdList.size());
                writer.name("elements").beginArray();
                for (final int value : intIdList) {
                    writer.value(value);
                }
                writer.endArray();
            });
        }

        @Override
        public <K, V> void writeMap(Map<K, V> map, Writer<K> keyWriter, Writer<V> valueWriter) {
            dump("map", () -> {
                writer.name("size").value(map.size());
                writer.name("elements").beginArray();
                for (final var entry : map.entrySet()) {
                    writer.beginObject();
                    writer.name("key");
                    dumpValue(entry.getKey(), keyWriter);
                    writer.name("value");
                    dumpValue(entry.getValue(), valueWriter);
                    writer.endObject();
                }
                writer.endArray();
            });
        }

        @Override
        public <E extends Enum<E>> void writeEnumSet(EnumSet<E> enumSet, Class<E> enumClass) {
            dump("enumSet", () -> {
                String className = enumClass.getName().replace('.', '/');
                String mojmapClassName = Objects.requireNonNullElse(MappingsHelper.namedOrIntermediaryToMojmap_class(className), className);
                mojmapClassName = mojmapClassName.substring(mojmapClassName.lastIndexOf('/') + 1);
                writer.name("enumClass").value(mojmapClassName);
                writer.name("size").value(enumSet.size());
                writer.name("elements").beginArray();
                for (final E element : enumSet) {
                    writer.value(element.name());
                }
                writer.endArray();
            });
        }

        @Override
        public <T> void writeOptional(Optional<T> optional, Writer<T> valueWriter) {
            writeNullable("optional", optional.orElse(null), valueWriter);
        }

        @Override
        public <T> void writeNullable(@Nullable T value, Writer<T> writer) {
            writeNullable("nullable", value, writer);
        }

        private <T> void writeNullable(String type, T value, Writer<T> valueWriter) {
            dump(type, () -> {
                writer.name("present");
                if (value != null) {
                    writer.value(true);
                    writer.name("value");
                    dumpValue(value, valueWriter);
                } else {
                    writer.value(false);
                }
            });
        }

        @Override
        public <L, R> void writeEither(Either<L, R> value, Writer<L> leftWriter, Writer<R> rightWriter) {
            dump("either", () -> {
                writer.name("either");
                value.ifLeft(left -> Uncheck.run(() -> {
                    writer.value("left");
                    writer.name("value");
                    dumpValue(left, leftWriter);
                })).ifRight(right -> Uncheck.run(() -> {
                    writer.value("right");
                    writer.name("value");
                    dumpValue(right, rightWriter);
                }));
            });
        }

        @Override
        public @NotNull PacketDumpByteBuf writeByteArray(byte[] array) {
            return dump("byteArray", () -> writer
                .name("length").value(array.length)
                .name("value").value(Base64.getEncoder().encodeToString(array))
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeVarIntArray(int[] array) {
            return dump("varIntArray", () -> {
                writer.name("length").value(array.length);
                writer.name("elements").beginArray();
                for (final int element : array) {
                    writer.value(element);
                }
                writer.endArray();
            });
        }

        @Override
        public @NotNull PacketDumpByteBuf writeLongArray(long[] array) {
            return dump("longArray", () -> {
                writer.name("length").value(array.length);
                writer.name("elements").beginArray();
                for (final long element : array) {
                    writer.value(element);
                }
                writer.endArray();
            });
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBlockPos(BlockPos pos) {
            return dump("blockPos", () -> writer
                .name("x").value(pos.getX())
                .name("y").value(pos.getY())
                .name("z").value(pos.getZ())
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeChunkPos(ChunkPos chunkPos) {
            return dump("chunkPos", () -> writer
                .name("x").value(chunkPos.x)
                .name("z").value(chunkPos.z)
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeSectionPos(SectionPos sectionPos) {
            return dump("sectionPos", () -> writer
                .name("x").value(sectionPos.x())
                .name("y").value(sectionPos.y())
                .name("z").value(sectionPos.z())
            );
        }

        @Override
        public void writeGlobalPos(GlobalPos pos) {
            dump("globalPos", () -> writer
                .name("level").value(pos.dimension().location().toString())
                .name("x").value(pos.pos().getX())
                .name("y").value(pos.pos().getY())
                .name("z").value(pos.pos().getZ())
            );
        }

        @Override
        public void writeVector3f(Vector3f vector3f) {
            dump("vector3f", () -> writer
                .name("x").value(vector3f.x)
                .name("y").value(vector3f.y)
                .name("z").value(vector3f.z)
            );
        }

        @Override
        public void writeQuaternion(Quaternionf quaternion) {
            dump("quaternion", () -> writer
                .name("x").value(quaternion.x)
                .name("y").value(quaternion.y)
                .name("z").value(quaternion.z)
                .name("w").value(quaternion.w)
            );
        }

        @Override
        public void writeVec3(Vec3 vec3) {
            dump("vec3", () -> writer
                .name("x").value(vec3.x)
                .name("y").value(vec3.y)
                .name("z").value(vec3.z)
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeComponent(Component component) {
            return dump("component", () -> {
                writer.name("value");
                GSON.toJson(Component.Serializer.toJsonTree(component), writer);
            });
        }

        @Override
        public @NotNull PacketDumpByteBuf writeEnum(Enum<?> value) {
            return dump("enum", () -> {
                String className = value.getDeclaringClass().getName().replace('.', '/');
                String mojmapClassName = Objects.requireNonNullElse(MappingsHelper.namedOrIntermediaryToMojmap_class(className), className);
                mojmapClassName = mojmapClassName.substring(mojmapClassName.lastIndexOf('/') + 1);
                writer
                    .name("enum").value(mojmapClassName)
                    .name("value").value(value.name());
            });
        }

        @Override
        public <T> @NotNull PacketDumpByteBuf writeById(ToIntFunction<T> idGetter, T value) {
            return dump("byId", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                writer.name("id").value(idGetter.applyAsInt(value));
            });
        }

        @Override
        public @NotNull PacketDumpByteBuf writeUUID(UUID uuid) {
            return dumpAsString("uuid", uuid);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeVarInt(int input) {
            return dumpSimple("varInt", input, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeVarLong(long value) {
            return dumpSimple("varLong", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeNbt(@Nullable Tag tag) {
            return dumpAsString("nbt", tag);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeItem(ItemStack stack) {
            return dump("item", () -> writer
                .name("item").value(stack.getItemHolder().unwrapKey().map(k -> k.location().toString()).orElse(null))
                .name("count").value(stack.getCount())
                .name("tag").value(Objects.toString(stack.getTag()))
            );
        }

        @Override
        public @NotNull FriendlyByteBuf writeUtf(String string) {
            return dump("utf", () -> writer
                .name("value").value(string)
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeUtf(String string, int maxLength) {
            return dump("utf", () -> writer
                .name("maxLength").value(maxLength)
                .name("value").value(string)
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeResourceLocation(ResourceLocation resourceLocation) {
            return dumpAsString("resourceLocation", resourceLocation);
        }

        @Override
        public void writeResourceKey(ResourceKey<?> resourceKey) {
            dump("resourceKey", () -> writer
                .name("registry").value(resourceKey.registry().toString())
                .name("location").value(resourceKey.location().toString())
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeDate(Date time) {
            return dumpSimple("date", ISO_8601.format(time), JsonWriter::value);
        }

        @Override
        public void writeInstant(Instant instant) {
            dumpAsString("instant", instant);
        }

        @Override
        public @NotNull PacketDumpByteBuf writePublicKey(PublicKey publicKey) {
            return dump("publicKey", () -> writer
                .name("encoded").value(Base64.getEncoder().encodeToString(publicKey.getEncoded()))
            );
        }

        @Override
        public void writeBlockHitResult(BlockHitResult result) {
            dump("blockHitResult", () -> writer
                .name("pos").beginObject()
                .name("x").value(result.getBlockPos().getX())
                .name("y").value(result.getBlockPos().getY())
                .name("z").value(result.getBlockPos().getZ()).endObject()
                .name("direction").value(result.getDirection().getSerializedName())
                .name("offset").beginObject()
                .name("x").value(result.getLocation().x - result.getBlockPos().getX())
                .name("y").value(result.getLocation().y - result.getBlockPos().getY())
                .name("z").value(result.getLocation().z - result.getBlockPos().getZ())
                .name("isInside").value(result.isInside())
            );
        }

        @Override
        public void writeBitSet(BitSet bitSet) {
            dump("bitSet", () -> {
                writer.name("bits").beginArray();
                IOStream.adapt(bitSet.stream().boxed()).forEach(writer::value);
                writer.endArray();
            });
        }

        @Override
        public void writeFixedBitSet(BitSet bitSet, int size) {
            dump("fixedBitSet", () -> {
                writer.name("size").value(size);
                writer.name("bits").beginArray();
                IOStream.adapt(bitSet.stream().boxed()).forEach(writer::value);
                writer.endArray();
            });
        }

        @Override
        public void writeGameProfile(GameProfile gameProfile) {
            dump("gameProfile", () -> {
                writer.name("value");
                GSON.toJson(gameProfile, GameProfile.class, writer);
            });
        }

        @Override
        public void writeGameProfileProperties(PropertyMap gameProfileProperties) {
            dump("gameProfileProperties", () -> {
                writer.name("value");
                GSON.toJson(gameProfileProperties, PropertyMap.class, writer);
            });
        }

        @Override
        public void writeProperty(Property property) {
            dump("property", () -> {
                writer.name("name").value(property.name());
                writer.name("value").value(property.value());
                if (property.hasSignature()) {
                    writer.name("signature").value(property.signature());
                }
            });
        }

        @Override
        public @NotNull PacketDumpByteBuf skipBytes(int length) {
            return dump("skipBytes", () -> writer.name("length").value(length));
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBoolean(boolean value) {
            return dumpSimple("boolean", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeByte(int value) {
            return dumpSimple("byte", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeShort(int value) {
            return dumpSimple("short", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeShortLE(int value) {
            return dumpSimple("shortLE", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeMedium(int value) {
            return dumpSimple("medium", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeMediumLE(int value) {
            return dumpSimple("mediumLE", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeInt(int value) {
            return dumpSimple("int", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeIntLE(int value) {
            return dumpSimple("intLE", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeLong(long value) {
            return dumpSimple("long", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeLongLE(long value) {
            return dumpSimple("longLE", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeChar(int value) {
            return dumpSimple("char", Character.toString((char)value), JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeFloat(float value) {
            return dumpSimple("float", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeFloatLE(float value) {
            return dumpSimple("floatLE", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeDouble(double value) {
            return dumpSimple("double", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeDoubleLE(double value) {
            return dumpSimple("doubleLE", value, JsonWriter::value);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBytes(ByteBuf source) {
            return writeBytes(source, source.readableBytes());
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBytes(ByteBuf source, int length) {
            final byte[] bytes = new byte[length];
            source.readBytes(bytes);
            return dumpBytes(bytes);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBytes(ByteBuf source, int sourceIndex, int length) {
            final byte[] bytes = new byte[length];
            source.getBytes(sourceIndex, bytes);
            return dumpBytes(bytes);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBytes(byte[] source) {
            return dumpBytes(source);
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBytes(byte[] source, int sourceIndex, int length) {
            return dumpBytes(Arrays.copyOfRange(source, sourceIndex, sourceIndex + length));
        }

        @Override
        public @NotNull PacketDumpByteBuf writeBytes(ByteBuffer source) {
            final byte[] bytes = new byte[source.remaining()];
            source.get(bytes);
            return dumpBytes(bytes);
        }

        @Override
        public int writeBytes(InputStream inputStream, int i) throws IOException {
            final byte[] bytes = new byte[i];
            final int read = inputStream.read(bytes);
            dumpBytes(Arrays.copyOf(bytes, i));
            return read;
        }

        @Override
        public int writeBytes(ScatteringByteChannel scatteringByteChannel, int i) throws IOException {
            final ByteBuffer buffer = ByteBuffer.allocate(i);
            final int read = scatteringByteChannel.read(buffer);
            buffer.flip();
            dumpBytes(Arrays.copyOfRange(
                buffer.array(),
                buffer.arrayOffset() + buffer.position(),
                buffer.arrayOffset() + buffer.limit()
            ));
            return read;
        }

        @Override
        public int writeBytes(FileChannel fileChannel, long l, int i) throws IOException {
            return writeBytes(fileChannel.position(l), i);
        }

        private PacketDumpByteBuf dumpBytes(byte[] bytes) {
            return dump("bytes", () -> writer
                .name("length").value(bytes.length)
                .name("value").value(Base64.getEncoder().encodeToString(bytes))
            );
        }

        @Override
        public @NotNull PacketDumpByteBuf writeZero(int length) {
            return dump("zero", () -> writer.name("length").value(length));
        }

        @Override
        public int writeCharSequence(CharSequence charSequence, Charset charset) {
            final String string = charSequence.toString();
            final byte[] encoded = string.getBytes(charset);
            dump("charSequence", () -> writer
                .name("charset").value(charset.name())
                .name("value").value(string)
                .name("encoded").value(Base64.getEncoder().encodeToString(encoded))
            );
            return encoded.length;
        }

        private void dumpValueClass(Object value) throws IOException {
            writer.name("valueClass");
            if (value != null) {
                String className = value.getClass().getName().replace('.', '/');
                String mojmapClassName = Objects.requireNonNullElse(MappingsHelper.namedOrIntermediaryToMojmap_class(className), className);
                mojmapClassName = mojmapClassName.substring(mojmapClassName.lastIndexOf('/') + 1);
                writer.value(mojmapClassName);
            } else {
                writer.nullValue();
            }
        }

        private <T> void dumpValue(T value, Writer<T> valueWriter) throws IOException {
            writer.beginObject();
            dumpValueClass(value);
            writer.name("fields").beginArray();
            valueWriter.accept(this, value);
            writer.endArray();
            writer.endObject();
        }

        private PacketDumpByteBuf dumpAsString(String type, Object value) {
            return dumpSimple(type, value != null ? value.toString() : null, JsonWriter::value);
        }

        private <T> PacketDumpByteBuf dumpSimple(String type, T value, IOBiConsumer<JsonWriter, T> valueWriter) {
            return dump(type, () -> {
                writer.name("value");
                valueWriter.accept(writer, value);
            });
        }

        private PacketDumpByteBuf dump(String type, IORunnable dumper) {
            Uncheck.run(() -> {
                writer.beginObject();
                writer.name("type").value(type);
                dumper.run();
                writer.endObject();
            });
            return this;
        }
    }
}
