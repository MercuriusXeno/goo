package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload: syncs the loaded ability definitions so the
 * client radial menu knows what abilities exist per goo type. Sends the
 * metadata needed for display (id, type, name, order) and the chain
 * block's fuse and stack ceiling the client predicts from, not the full
 * behavior configuration.
 *
 * @param entries the list of ability descriptors
 */
public record AbilitySyncPayload(List<Entry> entries) implements CustomPacketPayload {

    /**
     * Payload type ID for registration.
     */
    public static final Type<AbilitySyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Goo.MODID, "ability_sync"));

    /**
     * Stream codec for encoding/decoding.
     */
    public static final StreamCodec<FriendlyByteBuf, AbilitySyncPayload> STREAM_CODEC =
            StreamCodec.of(AbilitySyncPayload::encode, AbilitySyncPayload::decode);

    /**
     * Builds the sync payload from the current server ability registry.
     *
     * @return the payload with all loaded abilities
     */
    public static AbilitySyncPayload fromRegistry() {
        List<Entry> entries = new ArrayList<>();
        for (ResourceKey<GooTypeDefinition> type : GooTypes.order()) {
            entries.addAll(gloveEntries(type, AbilityRegistry.getAbilitiesForType(type)));
        }
        return new AbilitySyncPayload(entries);
    }

    /**
     * The entries the glove may offer for a type: every definition but the
     * tap-tagged ones, which only a tap's drip runs
     * (decision tap-ability-tagged-program).
     *
     * @param type        the goo type
     * @param definitions the type's definitions
     * @return the entries to sync
     */
    static List<Entry> gloveEntries(ResourceKey<GooTypeDefinition> type, List<AbilityDefinition> definitions) {
        return definitions.stream()
                .filter(def -> !def.hasTag(AbilityTags.TAP))
                .map(def -> new Entry(def.id().toString(), GooTypes.id(type),
                        def.displayName(), def.icon(), def.order(), def.tags(),
                        def.chain().fuseTicks(), def.chain().maxStacks()))
                .toList();
    }

    private static void encode(FriendlyByteBuf buf, AbilitySyncPayload payload) {
        buf.writeVarInt(payload.entries.size());
        for (Entry e : payload.entries) {
            buf.writeUtf(e.abilityId);
            buf.writeUtf(e.gooTypeId);
            buf.writeUtf(e.displayName);
            buf.writeUtf(e.icon);
            buf.writeVarInt(e.order);
            encodeTags(buf, e.tags);
            buf.writeVarInt(e.fuseTicks);
            buf.writeVarInt(e.maxStacks);
        }
    }

    private static void encodeTags(FriendlyByteBuf buf, List<String> tags) {
        buf.writeVarInt(tags.size());
        for (String tag : tags) {
            buf.writeUtf(tag);
        }
    }

    private static AbilitySyncPayload decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readVarInt(), decodeTags(buf), buf.readVarInt(), buf.readVarInt()));
        }
        return new AbilitySyncPayload(entries);
    }

    private static List<String> decodeTags(FriendlyByteBuf buf) {
        int tagCount = buf.readVarInt();
        List<String> tags = new ArrayList<>(tagCount);
        for (int j = 0; j < tagCount; j++) {
            tags.add(buf.readUtf());
        }
        return List.copyOf(tags);
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * A lightweight ability descriptor for client display.
     *
     * @param abilityId   the ability resource id string
     * @param gooTypeId   the goo type id string
     * @param displayName the translation key
     * @param icon        the icon texture path override (empty for convention path)
     * @param order       the sort order within the type
     * @param tags        categorical tags for targeting and display
     * @param fuseTicks   the chain block's full fuse
     * @param maxStacks   the chain block's stack ceiling
     */
    public record Entry(String abilityId, String gooTypeId, String displayName,
                        String icon, int order, List<String> tags, int fuseTicks, int maxStacks) {
    }
}
