package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.PrefabValues;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
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
 * Network payload carrying the full effective goo value map from server to client, and each prefab's value, which
 * an ability's cost may read (decision ability-cost-builds-as-a-one-type-by-type-value).
 * Wire format: VarInt entry count, then per entry: Identifier + VarInt type count + per type:
 * type key + VarLong numerator + VarLong denominator; then VarInt scalar prefab count, per prefab its name and its
 * fraction, then VarInt per-type prefab count, per prefab its name and its value as an entry's.
 *
 * @param values  the full effective goo value map
 * @param prefabs each prefab's resolved value
 */
public record GooValueSyncPayload(Map<Identifier, GooValue> values, PrefabValues prefabs)
        implements CustomPacketPayload {

    /**
     * A payload carrying no prefab.
     *
     * @param values the full effective goo value map
     */
    public GooValueSyncPayload(Map<Identifier, GooValue> values) {
        this(values, PrefabValues.EMPTY);
    }

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
        buf.writeVarInt(payload.prefabs.scalars().size());
        for (Map.Entry<String, GooFraction> scalar : payload.prefabs.scalars().entrySet()) {
            buf.writeUtf(scalar.getKey());
            buf.writeVarLong(scalar.getValue().numerator());
            buf.writeVarLong(scalar.getValue().denominator());
        }
        buf.writeVarInt(payload.prefabs.trees().size());
        for (Map.Entry<String, GooValue> tree : payload.prefabs.trees().entrySet()) {
            buf.writeUtf(tree.getKey());
            encodeGooValue(buf, tree.getValue());
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
        return new GooValueSyncPayload(values, decodePrefabs(buf));
    }

    private static PrefabValues decodePrefabs(FriendlyByteBuf buf) {
        int scalarCount = buf.readVarInt();
        Map<String, GooFraction> scalars = new HashMap<>(scalarCount);
        for (int i = 0; i < scalarCount; i++) {
            scalars.put(buf.readUtf(), GooFraction.of(buf.readVarLong(), buf.readVarLong()));
        }
        int treeCount = buf.readVarInt();
        Map<String, GooValue> trees = new HashMap<>(treeCount);
        for (int i = 0; i < treeCount; i++) {
            trees.put(buf.readUtf(), decodeGooValue(buf));
        }
        return new PrefabValues(scalars, trees);
    }

    /**
     * Writes a single GooValue: VarInt type count, then per type its key, a VarLong
     * numerator and a VarLong denominator.
     * Decision values-are-fractions-melt-floors-fabricate-ceils: the client reads the exact fraction.
     *
     * @param buf   the output buffer
     * @param value the goo value to encode
     */
    private static void encodeGooValue(FriendlyByteBuf buf, GooValue value) {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> all = value.exactAll();
        buf.writeVarInt(all.size());
        for (Map.Entry<ResourceKey<GooTypeDefinition>, GooFraction> entry : all.entrySet()) {
            GooTypes.KEY_STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeVarLong(entry.getValue().numerator());
            buf.writeVarLong(entry.getValue().denominator());
        }
    }

    /**
     * Reads a single GooValue written by {@link #encodeGooValue}.
     *
     * @param buf the input buffer
     * @return the decoded goo value
     */
    private static GooValue decodeGooValue(FriendlyByteBuf buf) {
        int typeCount = buf.readVarInt();
        Map<ResourceKey<GooTypeDefinition>, GooFraction> map = new LinkedHashMap<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            ResourceKey<GooTypeDefinition> type = GooTypes.KEY_STREAM_CODEC.decode(buf);
            map.put(type, GooFraction.of(buf.readVarLong(), buf.readVarLong()));
        }
        return GooValue.ofExact(map);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
