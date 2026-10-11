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

    private static final int SHIPPED_MOB_BADGES = 11;
    private static final int SHIPPED_WORLD_BADGES = 12;
    private static final int SHIPPED_SELF_BADGES = 4;
    private static final int SHIPPED_BREW_BADGES = 13;
    private static final int SHIPPED_CHANNELED_BADGES = 21;
    private static final int SHIPPED_FREE_BADGES = 6;
    private static final int SHIPPED_TAP_BADGES = 11;
    private static final int SHIPPED_PRISM_BADGES = 10;
    /**
     * The prism combos (decisions prism-hosts-the-combos, hive-prism-pillar-eats-the-living,
     * glacial-prism-holds-the-area-frozen, agitator-prism-quickens-until-a-spawn,
     * verdant-prism-greens-blocks-slowly, oculus-prism-becomes-a-hovering-eye,
     * reflector-rails-carry-the-brightest-light, timekeeper-prism-banks-ticks-forward-only).
     */
    private static final List<String> SHIPPED_PRISMS = List.of("nether_hive", "pulse_metronome", "pulse_relay",
            "frost_glacial", "hex_agitator", "leaf_verdant", "ender_oculus", "glow_reflector", "aeon_timekeeper",
            "typhoon_lift");
    /** The self + brew abilities, which wear brew on their self delivery (decision self-brew-goos-eat-before-the-effect). */
    private static final List<String> SHIPPED_BREWS = List.of("blaze_kindle", "ender_teleportitis", "leaf_barkskin",
            "rock_stoneskin", "vital_nourish", "shroom_sight", "nether_undead", "pulse_extender", "frost_iceborn",
            "hex_lifetap", "glow_lux", "aeon_haste", "typhoon_airborn");
    /** The shipped free abilities (decisions badge-vocabulary-gains-free-prism-tap-brew, zap-ticks-the-device-and-stuns). */
    private static final List<String> SHIPPED_FREE = List.of("unstable_explode", "rock_crush", "shroom_colonize",
            "pulse_zap", "frost_orb", "leaf_reap");
    /** Self deliveries wearing the channeled badge (decision flatten-disc-cursor-breaks-above-the-plane). */
    private static final List<String> SHIPPED_SELF_CHANNELS = List.of("rock_flatten", "frost_nova", "glow_scry",
            "glow_sunbeam", "glow_radiant", "typhoon_jet");
    /** Thrown deliveries wearing the channeled badge (decision spawn-goo-morphs-into-the-mob-it-births). */
    private static final List<String> SHIPPED_THROWN_CHANNELS = List.of("hex_spawn");

    @ParameterizedTest
    @CsvSource({"world, WORLD", "mob, MOB", "self, SELF", "channeled, CHANNELED", "brew, BREW",
            "free, FREE", "prism, PRISM", "tap, TAP"})
    void eachVocabularyWordParsesToItsBadge(String word, AbilityBadge expected) {
        assertEquals(expected, AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).getOrThrow());
    }

    /**
     * Each badge holds its own tier, ranked channeled, mob, world, free,
     * prism, tap, self, brew, at orders that reverse that rank if order alone
     * decided; a shared badge falls back to order (decision
     * fan-sorts-badge-then-order).
     */
    @Test
    void fanOrderRanksEachBadgeThenOrder() {
        record Ranked(String name, AbilityBadge badge, int order) {
        }
        List<Ranked> shuffled = List.of(
                new Ranked("brew", AbilityBadge.BREW, -3),
                new Ranked("tap", AbilityBadge.TAP, 0),
                new Ranked("world", AbilityBadge.WORLD, 3),
                new Ranked("self", AbilityBadge.SELF, -2),
                new Ranked("prism", AbilityBadge.PRISM, 1),
                new Ranked("mob_late", AbilityBadge.MOB, 5),
                new Ranked("free", AbilityBadge.FREE, 2),
                new Ranked("channeled", AbilityBadge.CHANNELED, 6),
                new Ranked("mob_early", AbilityBadge.MOB, 4));

        assertEquals(List.of("channeled", "mob_early", "mob_late", "world", "free", "prism", "tap", "self", "brew"),
                shuffled.stream().sorted(AbilityBadge.fanOrder(Ranked::badge, Ranked::order)).map(Ranked::name).toList());
    }

    @ParameterizedTest
    @CsvSource({"splash", "punch"})
    void aWordOutsideTheVocabularyFailsToParse(String word) {
        assertTrue(AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).isError());
    }

    /**
     * The badge a shipped ability wears: its delivery's own badge for a self
     * or a stream (decision one-proving-ability-per-kind), brew for a self +
     * brew ability, free for Blast, tap for a tap's drip ability, prism for
     * a prism combo such as Hive, channeled for a thrown channel such as Spawn, and for any
     * other thrown ability, mob where it targets entities and world elsewhere.
     *
     * @param definition the shipped ability
     * @return the badge it should wear
     */
    private static AbilityBadge expectedBadge(AbilityDefinition definition) {
        if (SHIPPED_FREE.contains(definition.id().getPath())) {
            return AbilityBadge.FREE;
        }
        if (SHIPPED_PRISMS.contains(definition.id().getPath())) {
            return AbilityBadge.PRISM;
        }
        if (definition.hasTag(AbilityTags.TAP)) {
            return AbilityBadge.TAP;
        }
        if (SHIPPED_THROWN_CHANNELS.contains(definition.id().getPath())) {
            return AbilityBadge.CHANNELED;
        }
        return switch (definition.delivery().kind()) {
            case SELF -> selfBadge(definition.id().getPath());
            case STREAM -> AbilityBadge.CHANNELED;
            default -> definition.hasTag(AbilityTags.ENTITY) ? AbilityBadge.MOB : AbilityBadge.WORLD;
        };
    }

    private static AbilityBadge selfBadge(String path) {
        if (SHIPPED_SELF_CHANNELS.contains(path)) {
            return AbilityBadge.CHANNELED;
        }
        return SHIPPED_BREWS.contains(path) ? AbilityBadge.BREW : AbilityBadge.SELF;
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
                        AbilityBadge.CHANNELED, (long) SHIPPED_CHANNELED_BADGES, AbilityBadge.FREE, (long) SHIPPED_FREE_BADGES,
                        AbilityBadge.TAP, (long) SHIPPED_TAP_BADGES, AbilityBadge.PRISM, (long) SHIPPED_PRISM_BADGES),
                shipped.values().stream().collect(Collectors.groupingBy(AbilityDefinition::badge, Collectors.counting())));
    }
}
