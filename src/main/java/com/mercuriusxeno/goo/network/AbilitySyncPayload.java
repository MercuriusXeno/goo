package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * metadata needed for display (id, type, name, order), the chain block's
 * step program,
 * whose params the marker's renderers read by the marker's ability id
 * (decision capability-interfaces-derive-host-kind), and the flat cost
 * the client prices a throw with (decision flat-cost-per-throw), and the
 * delivery the glove aims and throws by (decision delivery-block-in-ability-json).
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

    private static final StreamCodec<ByteBuf, List<Identifier>> REQUIRES_CODEC =
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, List<Step>> STEPS_CODEC = ByteBufCodecs.fromCodec(StepTypes.LIST_CODEC);

    /**
     * Builds the sync payload from a server's ability registry.
     *
     * @param registry the server's abilities
     * @return the payload with all loaded abilities
     */
    public static AbilitySyncPayload fromRegistry(AbilityRegistry registry) {
        List<Entry> entries = new ArrayList<>();
        for (ResourceKey<GooTypeDefinition> type : GooTypes.order()) {
            entries.addAll(gloveEntries(type, registry.getAbilitiesForType(type)));
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
    public static List<Entry> gloveEntries(ResourceKey<GooTypeDefinition> type, List<AbilityDefinition> definitions) {
        return definitions.stream()
                .filter(def -> !def.hasTag(AbilityTags.TAP))
                .map(def -> new Entry(def.id().toString(), GooTypes.id(type),
                        def.displayName(), def.icon(), def.order(), def.tags(),
                        def.behaviors(), def.cost(),
                        def.delivery(), def.badge(), def.requires()))
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
            STEPS_CODEC.encode(buf, e.behaviors);
            buf.writeVarInt(e.cost);
            Delivery.STREAM_CODEC.encode(buf, e.delivery);
            AbilityBadge.STREAM_CODEC.encode(buf, e.badge);
            REQUIRES_CODEC.encode(buf, e.requires);
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
                    buf.readUtf(), buf.readVarInt(), decodeTags(buf),
                    STEPS_CODEC.decode(buf), buf.readVarInt(), Delivery.STREAM_CODEC.decode(buf),
                    AbilityBadge.STREAM_CODEC.decode(buf), REQUIRES_CODEC.decode(buf)));
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
     * @param behaviors   the ability's step program
     * @param cost        the mB a throw costs, the same at every stack count
     * @param delivery    how the ability leaves the glove
     * @param badge       the target kind the radial marks on the icon
     * @param requires    the items a player must know before the radial offers it
     */
    public record Entry(String abilityId, String gooTypeId, String displayName,
                        String icon, int order, List<String> tags,
                        List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                        List<Identifier> requires) {
    }
}
