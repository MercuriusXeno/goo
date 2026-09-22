package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.data.GooValue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Network payload carrying the full effective goo value map from server to client.
 * Wire format: VarInt entry count, then per entry: Identifier + VarInt type count + per type: type key + VarInt amount.
 *
 * @param values the full effective goo value map
 */
public record GooValueSyncPayload(Map<Identifier, GooValue> values) implements CustomPacketPayload {

    /**
     * Payload type ID for registration.
     */
    public static final Type<GooValueSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "goo_value_sync"));

    /**
     * Stream codec for encoding/decoding the payload.
     */
    public static final StreamCodec<FriendlyByteBuf, GooValueSyncPayload> STREAM_CODEC =
            StreamCodec.of(GooValueSyncPayload::encode, GooValueSyncPayload::decode);

    /**
     * Writes the full value map to the buffer.
     *
     * @param buf     the output buffer
     * @param payload the payload to encode
     */
    private static void encode(FriendlyByteBuf buf, GooValueSyncPayload payload) {
        buf.writeVarInt(payload.values.size());
        for (Map.Entry<Identifier, GooValue> entry : payload.values.entrySet()) {
            buf.writeIdentifier(entry.getKey());
            encodeGooValue(buf, entry.getValue());
        }
    }

    /**
     * Reads the full value map from the buffer.
     *
     * @param buf the input buffer
     * @return the decoded payload
     */
    private static GooValueSyncPayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<Identifier, GooValue> values = new HashMap<>(count);
        for (int i = 0; i < count; i++) {
            Identifier id = buf.readIdentifier();
            values.put(id, decodeGooValue(buf));
        }
        return new GooValueSyncPayload(values);
    }

    /**
     * Writes a single GooValue: VarInt type count, then type key + amount pairs.
     *
     * @param buf   the output buffer
     * @param value the goo value to encode
     */
    private static void encodeGooValue(FriendlyByteBuf buf, GooValue value) {
        Map<ResourceKey<GooTypeDefinition>, Integer> all = value.getAll();
        buf.writeVarInt(all.size());
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : all.entrySet()) {
            GooTypes.KEY_STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    /**
     * Reads a single GooValue: VarInt type count, then type key + amount pairs.
     *
     * @param buf the input buffer
     * @return the decoded goo value
     */
    private static GooValue decodeGooValue(FriendlyByteBuf buf) {
        int typeCount = buf.readVarInt();
        return new GooValue(readTypeAmounts(buf, typeCount));
    }

    /**
     * Reads type key + amount pairs from the buffer into a map.
     *
     * @param buf   the input buffer
     * @param count the number of pairs to read
     * @return the decoded type-to-amount map
     */
    private static Map<ResourceKey<GooTypeDefinition>, Integer> readTypeAmounts(FriendlyByteBuf buf, int count) {
        Map<ResourceKey<GooTypeDefinition>, Integer> map = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            ResourceKey<GooTypeDefinition> type = GooTypes.KEY_STREAM_CODEC.decode(buf);
            map.put(type, buf.readVarInt());
        }
        return map;
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
