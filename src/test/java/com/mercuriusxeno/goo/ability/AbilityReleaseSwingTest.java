package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped goo_abilities JSON swings the glove hand on release only
 * where the ability throws a blob, and an ability that throws none keeps the
 * frame it held the arm in while the use key is held.
 * decision diagnose-and-correct-hand-swings-outside-throws
 */
class AbilityReleaseSwingTest {

    /** Each shipped ability that throws no blob, by file name, and the frame it holds the arm in. */
    private static final Map<String, ArmPoseKind> THROWS_NO_BLOB = Map.ofEntries(
            Map.entry("unstable_blast", ArmPoseKind.NONE),
            Map.entry("astral_meteo", ArmPoseKind.NONE),
            Map.entry("crystal_prism", ArmPoseKind.NONE),
            Map.entry("void_hole", ArmPoseKind.NONE),
            Map.entry("deep_click", ArmPoseKind.NONE),
            Map.entry("rock_spire", ArmPoseKind.NONE),
            Map.entry("hex_charm", ArmPoseKind.NONE),
            Map.entry("quantum_phase_other", ArmPoseKind.NONE),
            Map.entry("typhoon_shulk", ArmPoseKind.NONE),
            Map.entry("yore_rewind", ArmPoseKind.NONE),
            Map.entry("zoo_rally", ArmPoseKind.NONE),
            Map.entry("zoo_shape", ArmPoseKind.NONE),
            Map.entry("zoo_spawn", ArmPoseKind.NONE),
            Map.entry("glow_bulb", ArmPoseKind.HOLD_OUT),
            Map.entry("crystal_knives", ArmPoseKind.WIND_UP));

    /** Each shipped arc ability that throws a blob from the hand, by file name. */
    private static final Set<String> THROWS_A_BLOB = Set.of(
            "blaze_ignite", "deep_shriek", "ender_seal",
            "frost_orb", "leaf_bio", "leaf_bloom", "leaf_vines", "metal_dart",
            "quantum_portal", "quantum_tunnel", "shroom_spore", "unstable_lurker",
            "void_cancel", "weird_mitosis", "yore_age", "yore_expire", "zoo_allure");

    static Stream<String> abilitiesThrowingNoBlob() {
        return THROWS_NO_BLOB.keySet().stream().sorted();
    }

    static Stream<String> abilitiesThrowingABlob() {
        return THROWS_A_BLOB.stream().sorted();
    }

    @ParameterizedTest
    @MethodSource("abilitiesThrowingNoBlob")
    void anAbilityThrowingNoBlobPlaysNoSwingOnRelease(String name) {
        assertFalse(AbilityJson.decode(name).pose().swingsOnRelease(), name);
    }

    @ParameterizedTest
    @MethodSource("abilitiesThrowingNoBlob")
    void anAbilityThrowingNoBlobHoldsTheArmInItsFormerFrame(String name) {
        assertEquals(THROWS_NO_BLOB.get(name), AbilityJson.decode(name).pose().holdFrame(), name);
    }

    @ParameterizedTest
    @MethodSource("abilitiesThrowingABlob")
    void anArcAbilityThrowingABlobSwingsOnRelease(String name) {
        assertTrue(AbilityJson.decode(name).pose().swingsOnRelease(), name);
    }

    @Test
    void everyShippedArcAbilityIsSortedAsThrowingABlobOrNot() {
        List<String> arcs = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.delivery().kind() == DeliveryKind.ARC)
                .map(ability -> ability.id().getPath())
                .filter(name -> !THROWS_NO_BLOB.containsKey(name)).sorted().toList();
        assertEquals(THROWS_A_BLOB.stream().sorted().toList(), arcs);
    }
}
