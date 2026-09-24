package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The abilities synced to the client for the glove's radial leave out every
 * tap-tagged ability, which only a tap's drip runs.
 */
class AbilitySyncPayloadTest {

    private static AbilityDefinition ability(String name, int order, String tag) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.ROCK,
                name, "", order, null, null, List.of(), List.of(tag));
    }

    @Test
    void tapTaggedAbilitiesNeverReachTheGlove() {
        AbilityDefinition entity = ability("rock_throw", 1, AbilityTags.ENTITY);
        AbilityDefinition tap = ability("rock_tap", 0, AbilityTags.TAP);

        List<AbilitySyncPayload.Entry> synced = AbilitySyncPayload.gloveEntries(GooTypes.ROCK, List.of(tap, entity));

        assertEquals(List.of("goo:rock_throw"), synced.stream().map(AbilitySyncPayload.Entry::abilityId).toList());
    }
}
