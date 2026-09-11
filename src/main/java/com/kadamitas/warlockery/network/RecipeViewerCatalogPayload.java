package com.kadamitas.warlockery.network;

import com.kadamitas.warlockery.Warlockery;
import java.util.Arrays;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RecipeViewerCatalogPayload(
    long revision, int chunkIndex, int chunkCount, int totalBytes, byte[] data
) implements CustomPacketPayload {
    public static final int CHUNK_BYTES = 28 * 1024;
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    public static final Type<RecipeViewerCatalogPayload> TYPE = new Type<>(
        Identifier.fromNamespaceAndPath(Warlockery.MOD_ID, "recipe_viewer_catalog/v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeViewerCatalogPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, value) -> {
            buffer.writeLong(value.revision());
            buffer.writeVarInt(value.chunkIndex());
            buffer.writeVarInt(value.chunkCount());
            buffer.writeVarInt(value.totalBytes());
            buffer.writeByteArray(value.data);
        },
        buffer -> new RecipeViewerCatalogPayload(buffer.readLong(), buffer.readVarInt(), buffer.readVarInt(),
            buffer.readVarInt(), buffer.readByteArray(CHUNK_BYTES))
    );

    public RecipeViewerCatalogPayload {
        if (revision <= 0 || totalBytes <= 0 || totalBytes > MAX_BYTES
            || chunkCount != (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES
            || chunkIndex < 0 || chunkIndex >= chunkCount
            || data == null || data.length != Math.min(CHUNK_BYTES, totalBytes - chunkIndex * CHUNK_BYTES)) {
            throw new IllegalArgumentException("Invalid recipe catalog chunk");
        }
        data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() { return Arrays.copyOf(data, data.length); }

    @Override
    public Type<RecipeViewerCatalogPayload> type() { return TYPE; }
}
