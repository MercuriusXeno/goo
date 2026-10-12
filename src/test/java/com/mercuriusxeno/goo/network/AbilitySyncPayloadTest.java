package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The abilities synced to the client for the glove's radial leave out every
 * tap-tagged ability, which only a tap's drip runs, and carry each one's cost formula.
 */
class AbilitySyncPayloadTest {

    private static AbilityDefinition ability(String name, int order, String tag) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.ROCK,
                name, "", order, 0, Delivery.ARC, List.of(), List.of(tag), AbilityBadge.WORLD, List.of());
    }

    /**
     * Every shipped ability the glove offers, every one but a tap's drip
     * (decision tests-read-the-inventory-from-loaded-json).
     */
    static Stream<AbilityDefinition> shippedGloveAbilities() {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(definition -> !definition.hasTag(AbilityTags.TAP));
    }

    private static AbilitySyncPayload.Entry roundTrip(AbilityDefinition definition) {
        AbilitySyncPayload sent = new AbilitySyncPayload(
                AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        AbilitySyncPayload.STREAM_CODEC.encode(buf, sent);
        return AbilitySyncPayload.STREAM_CODEC.decode(buf).entries().getFirst();
    }

    /** The sync codec carries each cost formula to the client whole (decision unaffordable-click-does-nothing). */
    @ParameterizedTest
    @MethodSource("shippedGloveAbilities")
    void costRoundTripsThroughTheSyncCodec(AbilityDefinition definition) {
        assertEquals(definition.cost(), roundTrip(definition).cost(), definition.id().toString());
    }

    /** The sync codec carries each badge kind to the client unchanged (decision badge-marks-the-target-kind). */
    @ParameterizedTest
    @EnumSource(AbilityBadge.class)
    void badgeRoundTripsThroughTheSyncCodec(AbilityBadge badge) {
        AbilitySyncPayload sent = new AbilitySyncPayload(List.of(new AbilitySyncPayload.Entry("goo:rock_throw",
                "rock", "rock_throw", "", 0, List.of(), List.of(), 0, Delivery.ARC, badge, List.of())));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        AbilitySyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(badge, AbilitySyncPayload.STREAM_CODEC.decode(buf).entries().getFirst().badge());
    }

    /** The sync codec carries the items an ability requires to the client (decision ability-hidden-until-recipes-known). */
    @Test
    void requiresRoundTripsThroughTheSyncCodec() {
        AbilityDefinition definition = AbilityJson.decode("hex_charm");

        assertEquals(definition.requires(), roundTrip(definition).requires());
    }

    /**
     * The sync codec carries each shipped area to the client, the shape the glove
     * draws while right click is held (decision right-click-held-previews-release-throws),
     * razor's derived sphere among them (decision every-instant-aoe-shows-its-indicator-while-held).
     */
    @ParameterizedTest
    @MethodSource("shippedGloveAbilities")
    void eachShippedAreaRoundTripsThroughTheSyncCodec(AbilityDefinition definition) {
        assertEquals(definition.area(), roundTrip(definition).area(), definition.id().toString());
    }

    /**
     * Bulb, a world ability carrying its beacon as an on_prism reaction,
     * reaches the client with that reaction, so its petal wears the world
     * badge and the prism badge beside it.
     * decision on-prism-reaction-wears-the-prism-badge
     */
    @Test
    void bulbReachesTheClientWithItsPrismReaction() {
        AbilityDefinition bulb = AbilityJson.decode("glow_crystal");
        AbilitySyncPayload.Entry synced = roundTrip(bulb);

        assertEquals(AbilityBadge.WORLD, bulb.badge());
        assertFalse(bulb.onPrism().isEmpty(), "bulb's on_prism reaction");
        assertEquals(bulb.onPrism(), synced.onPrism());
        assertTrue(ClientAbility.fromEntry(synced).reactsToPrisms());
    }

    @Test
    void tapTaggedAbilitiesNeverReachTheGlove() {
        AbilityDefinition entity = ability("rock_throw", 1, AbilityTags.ENTITY);
        AbilityDefinition tap = ability("rock_tap", 0, AbilityTags.TAP);

        List<AbilitySyncPayload.Entry> synced = AbilitySyncPayload.gloveEntries(GooTypes.ROCK, List.of(tap, entity));

        assertEquals(List.of("goo:rock_throw"), synced.stream().map(AbilitySyncPayload.Entry::abilityId).toList());
    }
}
