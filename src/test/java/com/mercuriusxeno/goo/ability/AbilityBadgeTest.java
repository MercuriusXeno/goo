package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ability's badge reads only from the fixed vocabulary, and every shipped ability
 * declares mob exactly where it targets entities (decision badge-marks-the-target-kind).
 */
class AbilityBadgeTest {

    private static final int SHIPPED_MOB_BADGES = 16;
    private static final int SHIPPED_WORLD_BADGES = 14;

    @ParameterizedTest
    @CsvSource({"world, WORLD", "mob, MOB", "self, SELF", "punch, PUNCH", "channeled, CHANNELED"})
    void eachVocabularyWordParsesToItsBadge(String word, AbilityBadge expected) {
        assertEquals(expected, AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).getOrThrow());
    }

    @Test
    void aWordOutsideTheVocabularyFailsToParse() {
        assertTrue(AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("tap")).isError());
    }

    @Test
    void shippedAbilitiesWearMobExactlyWhereTheyTargetEntities() {
        Map<String, AbilityDefinition> shipped = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(definition -> definition.id().getPath(), Function.identity()));

        shipped.values().forEach(definition -> assertEquals(
                definition.hasTag(AbilityTags.ENTITY) ? AbilityBadge.MOB : AbilityBadge.WORLD,
                definition.badge(), definition.id().toString()));
        assertEquals(Map.of(AbilityBadge.MOB, (long) SHIPPED_MOB_BADGES, AbilityBadge.WORLD, (long) SHIPPED_WORLD_BADGES),
                shipped.values().stream().collect(Collectors.groupingBy(AbilityDefinition::badge, Collectors.counting())));
    }
}
