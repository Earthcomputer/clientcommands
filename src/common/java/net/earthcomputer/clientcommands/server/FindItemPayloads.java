package net.earthcomputer.clientcommands.server;

import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public final class FindItemPayloads {
    private FindItemPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> createType(String name) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("clientcommands", name));
    }

    public record Request(int requestId, String itemPredicate, boolean searchNested, boolean keepSearching, boolean allLoadedChunks) implements CustomPacketPayload {
        public static final Type<Request> TYPE = createType("find_item_request_v0");
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Request::requestId,
            ByteBufCodecs.STRING_UTF8, Request::itemPredicate,
            ByteBufCodecs.VAR_INT, Request::flags,
            Request::fromFlags
        );

        private static final int FLAG_SEARCH_NESTED = 1;
        private static final int FLAG_KEEP_SEARCHING = 1 << 1;
        private static final int FLAG_ALL_LOADED_CHUNKS = 1 << 2;

        private static Request fromFlags(int requestId, String itemPredicate, int flags) {
            return new Request(
                requestId,
                itemPredicate,
                (flags & FLAG_SEARCH_NESTED) != 0,
                (flags & FLAG_KEEP_SEARCHING) != 0,
                (flags & FLAG_ALL_LOADED_CHUNKS) != 0
            );
        }

        private int flags() {
            int flags = 0;
            if (searchNested) {
                flags |= FLAG_SEARCH_NESTED;
            }
            if (keepSearching) {
                flags |= FLAG_KEEP_SEARCHING;
            }
            if (allLoadedChunks) {
                flags |= FLAG_ALL_LOADED_CHUNKS;
            }
            return flags;
        }

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    public record Cancel(int requestId) implements CustomPacketPayload {
        public static final Type<Cancel> TYPE = createType("find_item_cancel_v0");
        public static final StreamCodec<ByteBuf, Cancel> CODEC = ByteBufCodecs.VAR_INT.map(Cancel::new, Cancel::requestId);

        @Override
        public Type<Cancel> type() {
            return TYPE;
        }
    }

    public record Match(int requestId, Either<BlockPos, EntityLocation> location, int count) implements CustomPacketPayload {
        public static final Type<Match> TYPE = createType("find_item_match_v0");
        public static final StreamCodec<RegistryFriendlyByteBuf, Match> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Match::requestId,
            ByteBufCodecs.either(BlockPos.STREAM_CODEC, EntityLocation.CODEC), Match::location,
            ByteBufCodecs.VAR_INT, Match::count,
            Match::new
        );

        @Override
        public Type<Match> type() {
            return TYPE;
        }
    }

    public record EntityLocation(UUID uuid, Component name, BlockPos pos) {
        public static final StreamCodec<RegistryFriendlyByteBuf, EntityLocation> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, EntityLocation::uuid,
            ComponentSerialization.TRUSTED_STREAM_CODEC, EntityLocation::name,
            BlockPos.STREAM_CODEC, EntityLocation::pos,
            EntityLocation::new
        );
    }

    public record Finished(int requestId, Reason reason) implements CustomPacketPayload {
        public static final Type<Finished> TYPE = createType("find_item_finished_v0");
        public static final StreamCodec<ByteBuf, Finished> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Finished::requestId,
            ByteBufCodecs.idMapper(i -> Reason.values()[i], Reason::ordinal), Finished::reason,
            Finished::new
        );

        @Override
        public Type<Finished> type() {
            return TYPE;
        }

        public enum Reason {
            COMPLETED,
            CANCELLED,
            NO_PERMISSION,
            INVALID_ITEM,
        }
    }
}
