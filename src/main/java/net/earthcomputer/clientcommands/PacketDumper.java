package net.earthcomputer.clientcommands;

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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.apache.commons.io.function.IOBiConsumer;
import org.apache.commons.io.function.IORunnable;
import org.apache.commons.io.function.IOStream;
import org.apache.commons.io.function.Uncheck;
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
import java.util.*;
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

    public static class PacketDumpByteBuf extends PacketByteBuf {
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
            super(Unpooled.buffer(0, 0));
            this.writer = writer;
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> PacketDumpByteBuf encode(DynamicOps<NbtElement> ops, Codec<T> codec, T value) {
            return dump("withCodec", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                writer.name("encodedNbt").value(Util.getResult(
                    codec.encodeStart(ops, value),
                    message -> new EncoderException("Failed to encode: " + message + " " + value)
                ).toString());
                writer.name("encodedJson");
                GSON.toJson(Util.getResult(
                    codec.encodeStart(JsonOps.INSTANCE, value),
                    message -> new EncoderException("Failed to encode: " + message + " " + value)
                ), writer);
            });
        }

        @Override
        public <T> void encodeAsJson(Codec<T> codec, T value) {
            dump("jsonWithCodec", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                writer.name("encodedJson");
                GSON.toJson(Util.getResult(
                    codec.encodeStart(JsonOps.INSTANCE, value),
                    message -> new EncoderException("Failed to encode: " + message + " " + value)
                ), writer);
            });
        }

        @Override
        public <T> void writeRegistryValue(IndexedIterable<T> indexedIterable, T value) {
            dump("id", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                if (indexedIterable instanceof Registry<T> registry) {
                    writer.name("registry").value(registry.getKey().getValue().toString());
                    writer.name("valueKey").value(Objects.toString(registry.getId(value)));
                }
                writer.name("id").value(indexedIterable.getRawId(value));
            });
        }

        @Override
        public <T> void writeRegistryEntry(IndexedIterable<RegistryEntry<T>> idMap, RegistryEntry<T> value, PacketWriter<T> directWriter) {
            dump("idHolder", () -> {
                writer.name("kind").value(value.getType().name());
                value.getKeyOrValue().ifLeft(key -> Uncheck.run(() -> {
                    writer.name("referenceKey").value(key.getValue().toString());
                    writer.name("id").value(idMap.getRawId(value));
                })).ifRight(directValue -> Uncheck.run(() -> {
                    writer.name("directValue");
                    dumpValue(directValue, directWriter);
                }));
            });
        }

        @Override
        public <T> void writeCollection(Collection<T> collection, PacketWriter<T> elementWriter) {
            dump("collection", () -> {
                writer.name("size").value(collection.size());
                writer.name("elements").beginArray();
                for (T element : collection) {
                    dumpValue(element, elementWriter);
                }
                writer.endArray();
            });
        }

        @Override
        public void writeIntList(IntList intIdList) {
            dump("intIdList", () -> {
                writer.name("size").value(intIdList.size());
                writer.name("elements").beginArray();
                for (int value : intIdList) {
                    writer.value(value);
                }
                writer.endArray();
            });
        }

        @Override
        public <K, V> void writeMap(Map<K, V> map, PacketWriter<K> keyWriter, PacketWriter<V> valueWriter) {
            dump("map", () -> {
                writer.name("size").value(map.size());
                writer.name("elements").beginArray();
                for (var entry : map.entrySet()) {
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
                writer.name("enumClass").value(MappingsHelper.namedOrIntermediaryToMojmap_class(enumClass.getName().replace('.', '/')).orElseThrow());
                writer.name("size").value(enumSet.size());
                writer.name("elements").beginArray();
                for (E element : enumSet) {
                    writer.value(element.name());
                }
                writer.endArray();
            });
        }

        @Override
        public <T> void writeOptional(Optional<T> optional, PacketWriter<T> valueWriter) {
            writeNullable("optional", optional.orElse(null), valueWriter);
        }

        @Override
        public <T> void writeNullable(@Nullable T value, PacketWriter<T> writer) {
            writeNullable("nullable", value, writer);
        }

        private <T> void writeNullable(String type, T value, PacketWriter<T> valueWriter) {
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
        public <L, R> void writeEither(Either<L, R> value, PacketWriter<L> leftWriter, PacketWriter<R> rightWriter) {
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
        public PacketDumpByteBuf writeByteArray(byte[] array) {
            return dump("byteArray", () -> writer
                .name("length").value(array.length)
                .name("value").value(Base64.getEncoder().encodeToString(array))
            );
        }

        @Override
        public PacketDumpByteBuf writeIntArray(int[] array) {
            return dump("varIntArray", () -> {
                writer.name("length").value(array.length);
                writer.name("elements").beginArray();
                for (int element : array) {
                    writer.value(element);
                }
                writer.endArray();
            });
        }

        @Override
        public PacketDumpByteBuf writeLongArray(long[] array) {
            return dump("longArray", () -> {
                writer.name("length").value(array.length);
                writer.name("elements").beginArray();
                for (long element : array) {
                    writer.value(element);
                }
                writer.endArray();
            });
        }

        @Override
        public PacketDumpByteBuf writeBlockPos(BlockPos pos) {
            return dump("blockPos", () -> writer
                .name("x").value(pos.getX())
                .name("y").value(pos.getY())
                .name("z").value(pos.getZ())
            );
        }

        @Override
        public PacketDumpByteBuf writeChunkPos(ChunkPos chunkPos) {
            return dump("chunkPos", () -> writer
                .name("x").value(chunkPos.x)
                .name("z").value(chunkPos.z)
            );
        }

        @Override
        public PacketDumpByteBuf writeChunkSectionPos(ChunkSectionPos chunkSectionPos) {
            return dump("sectionPos", () -> writer
                .name("x").value(chunkSectionPos.getSectionX())
                .name("y").value(chunkSectionPos.getSectionY())
                .name("z").value(chunkSectionPos.getSectionZ())
            );
        }

        @Override
        public void writeGlobalPos(GlobalPos pos) {
            dump("globalPos", () -> writer
                .name("level").value(pos.getDimension().getValue().toString())
                .name("x").value(pos.getPos().getX())
                .name("y").value(pos.getPos().getY())
                .name("z").value(pos.getPos().getZ())
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
        public void writeQuaternionf(Quaternionf quaternion) {
            dump("quaternion", () -> writer
                .name("x").value(quaternion.x)
                .name("y").value(quaternion.y)
                .name("z").value(quaternion.z)
                .name("w").value(quaternion.w)
            );
        }

        @Override
        public void writeVec3d(Vec3d vec3) {
            dump("vec3", () -> writer
                .name("x").value(vec3.x)
                .name("y").value(vec3.y)
                .name("z").value(vec3.z)
            );
        }

        @Override
        public PacketDumpByteBuf writeText(Text text) {
            return dump("component", () -> {
                writer.name("value");
                GSON.toJson(Text.Serialization.toJsonTree(text), writer);
            });
        }

        @Override
        public PacketDumpByteBuf writeEnumConstant(Enum<?> value) {
            return dump("enum", () -> writer
                .name("enum").value(value.getDeclaringClass().getName())
                .name("value").value(value.name())
            );
        }

        @Override
        public <T> PacketDumpByteBuf encode(ToIntFunction<T> idGetter, T value) {
            return dump("byId", () -> {
                dumpValueClass(value);
                writer.name("value").value(Objects.toString(value));
                writer.name("id").value(idGetter.applyAsInt(value));
            });
        }

        @Override
        public PacketDumpByteBuf writeUuid(UUID uuid) {
            return dumpAsString("uuid", uuid);
        }

        @Override
        public PacketDumpByteBuf writeVarInt(int input) {
            return dumpSimple("varInt", input, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeVarLong(long value) {
            return dumpSimple("varLong", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeNbt(@Nullable NbtElement NbtElement) {
            return dumpAsString("nbt", NbtElement);
        }

        @Override
        public PacketDumpByteBuf writeItemStack(ItemStack stack) {
            return dump("item", () -> writer
                .name("item").value(stack.getRegistryEntry().getKey().map(k -> k.getValue().toString()).orElse(null))
                .name("count").value(stack.getCount())
                .name("tag").value(Objects.toString(stack.getNbt()))
            );
        }

        @Override
        public PacketByteBuf writeString(String string) {
            return dump("utf", () -> writer
                .name("value").value(string)
            );
        }

        @Override
        public PacketDumpByteBuf writeString(String string, int maxLength) {
            return dump("utf", () -> writer
                .name("maxLength").value(maxLength)
                .name("value").value(string)
            );
        }

        @Override
        public PacketDumpByteBuf writeIdentifier(Identifier identifier) {
            return dumpAsString("resourceLocation", identifier);
        }

        @Override
        public void writeRegistryKey(RegistryKey<?> registryKey) {
            dump("resourceKey", () -> writer
                .name("registry").value(registryKey.getRegistry().toString())
                .name("location").value(registryKey.getValue().toString())
            );
        }

        @Override
        public PacketDumpByteBuf writeDate(Date time) {
            return dumpSimple("date", ISO_8601.format(time), JsonWriter::value);
        }

        @Override
        public void writeInstant(Instant instant) {
            dumpAsString("instant", instant);
        }

        @Override
        public PacketDumpByteBuf writePublicKey(PublicKey publicKey) {
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
                .name("direction").value(result.getSide().getName())
                .name("offset").beginObject()
                .name("x").value(result.getPos().x - result.getBlockPos().getX())
                .name("y").value(result.getPos().y - result.getBlockPos().getY())
                .name("z").value(result.getPos().z - result.getBlockPos().getZ()).endObject()
                .name("isInside").value(result.isInsideBlock())
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
        public void writeBitSet(BitSet bitSet, int size) {
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
        public void writePropertyMap(PropertyMap gameProfileProperties) {
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
        public PacketDumpByteBuf skipBytes(int length) {
            return dump("skipBytes", () -> writer.name("length").value(length));
        }

        @Override
        public PacketDumpByteBuf writeBoolean(boolean value) {
            return dumpSimple("boolean", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeByte(int value) {
            return dumpSimple("byte", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeShort(int value) {
            return dumpSimple("short", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeShortLE(int value) {
            return dumpSimple("shortLE", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeMedium(int value) {
            return dumpSimple("medium", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeMediumLE(int value) {
            return dumpSimple("mediumLE", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeInt(int value) {
            return dumpSimple("int", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeIntLE(int value) {
            return dumpSimple("intLE", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeLong(long value) {
            return dumpSimple("long", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeLongLE(long value) {
            return dumpSimple("longLE", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeChar(int value) {
            return dumpSimple("char", Character.toString((char) value), JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeFloat(float value) {
            return dumpSimple("float", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeFloatLE(float value) {
            return dumpSimple("floatLE", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeDouble(double value) {
            return dumpSimple("double", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeDoubleLE(double value) {
            return dumpSimple("doubleLE", value, JsonWriter::value);
        }

        @Override
        public PacketDumpByteBuf writeBytes(ByteBuf source) {
            return writeBytes(source, source.readableBytes());
        }

        @Override
        public PacketDumpByteBuf writeBytes(ByteBuf source, int length) {
            byte[] bytes = new byte[length];
            source.readBytes(bytes);
            return dumpBytes(bytes);
        }

        @Override
        public PacketDumpByteBuf writeBytes(ByteBuf source, int sourceIndex, int length) {
            byte[] bytes = new byte[length];
            source.getBytes(sourceIndex, bytes);
            return dumpBytes(bytes);
        }

        @Override
        public PacketDumpByteBuf writeBytes(byte[] source) {
            return dumpBytes(source);
        }

        @Override
        public PacketDumpByteBuf writeBytes(byte[] source, int sourceIndex, int length) {
            return dumpBytes(Arrays.copyOfRange(source, sourceIndex, sourceIndex + length));
        }

        @Override
        public PacketDumpByteBuf writeBytes(ByteBuffer source) {
            byte[] bytes = new byte[source.remaining()];
            source.get(bytes);
            return dumpBytes(bytes);
        }

        @Override
        public int writeBytes(InputStream inputStream, int i) throws IOException {
            byte[] bytes = new byte[i];
            int read = inputStream.read(bytes);
            dumpBytes(Arrays.copyOf(bytes, i));
            return read;
        }

        @Override
        public int writeBytes(ScatteringByteChannel scatteringByteChannel, int i) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(i);
            int read = scatteringByteChannel.read(buffer);
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
        public PacketDumpByteBuf writeZero(int length) {
            return dump("zero", () -> writer.name("length").value(length));
        }

        @Override
        public int writeCharSequence(CharSequence charSequence, Charset charset) {
            String string = charSequence.toString();
            byte[] encoded = string.getBytes(charset);
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
                writer.value(MappingsHelper.namedOrIntermediaryToMojmap_class(value.getClass().getName().replace('.', '/')).orElseThrow());
            } else {
                writer.nullValue();
            }
        }

        private <T> void dumpValue(T value, PacketWriter<T> valueWriter) throws IOException {
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
