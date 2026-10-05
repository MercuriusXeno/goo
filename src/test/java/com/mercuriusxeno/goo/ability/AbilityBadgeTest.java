package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
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
    private static final int SHIPPED_WORLD_BADGES = 7;
    private static final int SHIPPED_SELF_BADGES = 2;
    private static final int SHIPPED_BREW_BADGES = 2;
    private static final int SHIPPED_CHANNELED_BADGES = 1;
    /** The self + brew abilities, which wear brew on their self delivery (decision self-brew-goos-eat-before-the-effect). */
    private static final List<String> SHIPPED_BREWS = List.of("blaze_kindle", "leaf_barkskin");

    @ParameterizedTest
    @CsvSource({"world, WORLD", "mob, MOB", "self, SELF", "channeled, CHANNELED", "brew, BREW"})
    void eachVocabularyWordParsesToItsBadge(String word, AbilityBadge expected) {
        assertEquals(expected, AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).getOrThrow());
    }

    /**
     * Each badge holds its own tier, ranked channeled, mob, world, self,
     * brew, at orders that would reverse that rank if order alone decided; a
     * shared badge falls back to order (decision fan-sorts-badge-then-order).
     */
    @Test
    void fanOrderRanksEachBadgeThenOrder() {
        record Ranked(String name, AbilityBadge badge, int order) {
        }
        List<Ranked> shuffled = List.of(
                new Ranked("brew", AbilityBadge.BREW, -1),
                new Ranked("world", AbilityBadge.WORLD, 1),
                new Ranked("self", AbilityBadge.SELF, 0),
                new Ranked("mob_late", AbilityBadge.MOB, 2),
                new Ranked("channeled", AbilityBadge.CHANNELED, 4),
                new Ranked("mob_early", AbilityBadge.MOB, 1));

        assertEquals(List.of("channeled", "mob_early", "mob_late", "world", "self", "brew"),
                shuffled.stream().sorted(AbilityBadge.fanOrder(Ranked::badge, Ranked::order)).map(Ranked::name).toList());
    }

    @ParameterizedTest
    @CsvSource({"tap", "punch"})
    void aWordOutsideTheVocabularyFailsToParse(String word) {
        assertTrue(AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).isError());
    }

    /**
     * The badge a shipped ability wears: its delivery's own badge for a self
     * or a stream (decision one-proving-ability-per-kind), brew for a self +
     * brew ability, and for a thrown ability, mob where it targets entities
     * and world elsewhere.
     *
     * @param definition the shipped ability
     * @return the badge it should wear
     */
    private static AbilityBadge expectedBadge(AbilityDefinition definition) {
        return switch (definition.delivery().kind()) {
            case SELF -> SHIPPED_BREWS.contains(definition.id().getPath()) ? AbilityBadge.BREW : AbilityBadge.SELF;
            case STREAM -> AbilityBadge.CHANNELED;
            default -> definition.hasTag(AbilityTags.ENTITY) ? AbilityBadge.MOB : AbilityBadge.WORLD;
        };
    }

    @Test
    void shippedAbilitiesWearMobExactlyWhereTheyTargetEntities() {
        Map<String, AbilityDefinition> shipped = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(definition -> definition.id().getPath(), Function.identity()));

        shipped.values().forEach(definition -> assertEquals(
                expectedBadge(definition), definition.badge(), definition.id().toString()));
        assertEquals(Map.of(AbilityBadge.MOB, (long) SHIPPED_MOB_BADGES, AbilityBadge.WORLD, (long) SHIPPED_WORLD_BADGES,
                        AbilityBadge.SELF, (long) SHIPPED_SELF_BADGES, AbilityBadge.BREW, (long) SHIPPED_BREW_BADGES,
                        AbilityBadge.CHANNELED, (long) SHIPPED_CHANNELED_BADGES),
                shipped.values().stream().collect(Collectors.groupingBy(AbilityDefinition::badge, Collectors.counting())));
    }
}
