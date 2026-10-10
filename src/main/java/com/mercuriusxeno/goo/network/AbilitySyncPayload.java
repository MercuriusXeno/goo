package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
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
 * delivery the glove aims and throws by (decision delivery-block-in-ability-json),
 * and the items a throw consumes, so the client refuses a throw it cannot pay
 * (decision ability-json-names-its-reagent), and the upkeep a held self +
 * brew effect pays each tick (decision self-effects-trickle-until-ended),
 * and what a blink adds for its trip, so the HUD shows the live cost
 * (decision blink-lands-safely-costed-by-distance).
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

    private static final StreamCodec<ByteBuf, List<Identifier>> ITEMS_CODEC =
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
                        def.delivery(), def.badge(), def.requires(), def.area(), def.indicator(),
                        def.consumes(), def.upkeep(), def.distancePrice()))
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
            ITEMS_CODEC.encode(buf, e.requires);
            AbilityArea.STREAM_CODEC.encode(buf, e.area);
            IndicatorShowing.STREAM_CODEC.encode(buf, e.indicator);
            ITEMS_CODEC.encode(buf, e.consumes);
            buf.writeVarInt(e.upkeep);
            DistancePrice.STREAM_CODEC.encode(buf, e.distancePrice);
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
                    AbilityBadge.STREAM_CODEC.decode(buf), ITEMS_CODEC.decode(buf),
                    AbilityArea.STREAM_CODEC.decode(buf), IndicatorShowing.STREAM_CODEC.decode(buf),
                    ITEMS_CODEC.decode(buf), buf.readVarInt(), DistancePrice.STREAM_CODEC.decode(buf)));
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
     * @param area        the area the glove draws while right click is held
     * @param indicator   when the ability's indicator shows
     * @param consumes    the items a throw takes, one of each, beside its goo cost
     * @param upkeep      the mB a held self + brew effect pays each tick it stands
     * @param distancePrice what a blink adds to the cost for its trip
     */
    public record Entry(String abilityId, String gooTypeId, String displayName,
                        String icon, int order, List<String> tags,
                        List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                        List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                        List<Identifier> consumes, int upkeep, DistancePrice distancePrice) {

        /**
         * An entry whose cost reads no trip.
         *
         * @param abilityId   the ability resource id string
         * @param gooTypeId   the goo type id string
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order within the type
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items a player must know before the radial offers it
         * @param area        the area the glove draws while right click is held
         * @param indicator   when the ability's indicator shows
         * @param consumes    the items a throw takes, one of each
         * @param upkeep      the mB a held self + brew effect pays each tick it stands
         */
        public Entry(String abilityId, String gooTypeId, String displayName, String icon, int order,
                     List<String> tags, List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                     List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                     List<Identifier> consumes, int upkeep) {
            this(abilityId, gooTypeId, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires,
                    area, indicator, consumes, upkeep, DistancePrice.NONE);
        }

        /**
         * An entry paying no upkeep.
         *
         * @param abilityId   the ability resource id string
         * @param gooTypeId   the goo type id string
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order within the type
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items a player must know before the radial offers it
         * @param area        the area the glove draws while right click is held
         * @param indicator   when the ability's indicator shows
         * @param consumes    the items a throw takes, one of each
         */
        public Entry(String abilityId, String gooTypeId, String displayName, String icon, int order,
                     List<String> tags, List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                     List<Identifier> requires, AbilityArea area, IndicatorShowing indicator,
                     List<Identifier> consumes) {
            this(abilityId, gooTypeId, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires,
                    area, indicator, consumes, AbilityDefinition.NO_UPKEEP);
        }

        /**
         * An entry consuming no item beside its goo cost.
         *
         * @param abilityId   the ability resource id string
         * @param gooTypeId   the goo type id string
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order within the type
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items a player must know before the radial offers it
         * @param area        the area the glove draws while right click is held
         * @param indicator   when the ability's indicator shows
         */
        public Entry(String abilityId, String gooTypeId, String displayName, String icon, int order,
                     List<String> tags, List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                     List<Identifier> requires, AbilityArea area, IndicatorShowing indicator) {
            this(abilityId, gooTypeId, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires,
                    area, indicator, List.of());
        }

        /**
         * An entry declaring no area.
         *
         * @param abilityId   the ability resource id string
         * @param gooTypeId   the goo type id string
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order within the type
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items a player must know before the radial offers it
         */
        public Entry(String abilityId, String gooTypeId, String displayName, String icon, int order,
                     List<String> tags, List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                     List<Identifier> requires) {
            this(abilityId, gooTypeId, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires,
                    AbilityArea.NONE);
        }

        /**
         * An entry whose indicator shows while right click is held.
         *
         * @param abilityId   the ability resource id string
         * @param gooTypeId   the goo type id string
         * @param displayName the translation key
         * @param icon        the icon texture path override
         * @param order       the sort order within the type
         * @param tags        categorical tags
         * @param behaviors   the ability's step program
         * @param cost        the mB a throw costs
         * @param delivery    how the ability leaves the glove
         * @param badge       the target kind the radial marks on the icon
         * @param requires    the items a player must know before the radial offers it
         * @param area        the area the glove draws while right click is held
         */
        public Entry(String abilityId, String gooTypeId, String displayName, String icon, int order,
                     List<String> tags, List<Step> behaviors, int cost, Delivery delivery, AbilityBadge badge,
                     List<Identifier> requires, AbilityArea area) {
            this(abilityId, gooTypeId, displayName, icon, order, tags, behaviors, cost, delivery, badge, requires,
                    area, IndicatorShowing.HELD);
        }
    }
}
