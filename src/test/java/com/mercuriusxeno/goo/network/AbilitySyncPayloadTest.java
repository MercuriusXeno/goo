package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.type.GooTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The abilities synced to the client for the glove's radial leave out every
 * tap-tagged ability, which only a tap's drip runs, and carry each one's cost formula.
 */
class AbilitySyncPayloadTest {

    private static AbilityDefinition ability(String name, int order, String tag) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.ROCK,
                name, "", order, 0, Delivery.ARC, List.of(), List.of(tag), AbilityBadge.WORLD);
    }

    /** The sync codec carries each cost formula to the client whole (decision unaffordable-click-does-nothing). */
    @ParameterizedTest
    @ValueSource(strings = {"crystal_cloud", "unstable_timed_bomb", "unstable_proximity_mine"})
    void costRoundTripsThroughTheSyncCodec(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        AbilitySyncPayload sent = new AbilitySyncPayload(
                AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        AbilitySyncPayload.STREAM_CODEC.encode(buf, sent);
        AbilitySyncPayload received = AbilitySyncPayload.STREAM_CODEC.decode(buf);

        assertEquals(definition.cost(), received.entries().getFirst().cost());
    }

    /** The sync codec carries each badge kind to the client unchanged (decision badge-marks-the-target-kind). */
    @ParameterizedTest
    @EnumSource(AbilityBadge.class)
    void badgeRoundTripsThroughTheSyncCodec(AbilityBadge badge) {
        AbilitySyncPayload sent = new AbilitySyncPayload(List.of(new AbilitySyncPayload.Entry("goo:rock_throw",
                "rock", "rock_throw", "", 0, List.of(), List.of(), 0, Delivery.ARC, badge)));
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());

        AbilitySyncPayload.STREAM_CODEC.encode(buf, sent);

        assertEquals(badge, AbilitySyncPayload.STREAM_CODEC.decode(buf).entries().getFirst().badge());
    }

    @Test
    void tapTaggedAbilitiesNeverReachTheGlove() {
        AbilityDefinition entity = ability("rock_throw", 1, AbilityTags.ENTITY);
        AbilityDefinition tap = ability("rock_tap", 0, AbilityTags.TAP);

        List<AbilitySyncPayload.Entry> synced = AbilitySyncPayload.gloveEntries(GooTypes.ROCK, List.of(tap, entity));

        assertEquals(List.of("goo:rock_throw"), synced.stream().map(AbilitySyncPayload.Entry::abilityId).toList());
    }
}
