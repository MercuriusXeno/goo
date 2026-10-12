package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every shipped goo_abilities JSON resolves to the arm pose the operator's
 * table ruled for it, its delivery kind's default or the override its JSON
 * names (decision ability-json-names-its-arm-pose).
 */
class AbilityPoseTableTest {

    /** The ruled table: each shipped ability by file name and the pose it resolves to. */
    private static final Map<String, ArmPose> RULED = Map.ofEntries(
            Map.entry("astral_gate", ArmPose.THROWN),
            Map.entry("astral_meteo", ArmPose.THROWN),
            Map.entry("astral_scry", ArmPose.HELD_UP),
            Map.entry("blaze_ignite", ArmPose.THROWN),
            Map.entry("blaze_kindle", ArmPose.HELD_UP),
            Map.entry("blaze_spitfire", ArmPose.HELD_OUT),
            Map.entry("crystal_cloud", ArmPose.THROWN),
            Map.entry("crystal_glitter", ArmPose.HELD_UP),
            Map.entry("crystal_prism", ArmPose.THROWN),
            Map.entry("crystal_scales", ArmPose.HELD_UP),
            Map.entry("crystal_shards", ArmPose.SHOULDER_CAST),
            Map.entry("crystal_shards_tap", ArmPose.THROWN),
            Map.entry("deep_churn", ArmPose.THROWN),
            Map.entry("deep_convoke", ArmPose.THROWN),
            Map.entry("deep_dive", ArmPose.HELD_UP),
            Map.entry("ender_blink", ArmPose.HELD_OUT),
            Map.entry("ender_convoke_tap", ArmPose.THROWN),
            Map.entry("ender_end", ArmPose.THROWN),
            Map.entry("ender_oculus", ArmPose.THROWN),
            Map.entry("ender_shifter", ArmPose.HELD_UP),
            Map.entry("ender_zone", ArmPose.THROWN),
            Map.entry("frost_cold", ArmPose.HELD_OUT),
            Map.entry("frost_glacial", ArmPose.THROWN),
            Map.entry("frost_iceborn", ArmPose.HELD_UP),
            Map.entry("frost_nova", ArmPose.HELD_UP),
            Map.entry("frost_nova_tap", ArmPose.THROWN),
            Map.entry("frost_orb", ArmPose.THROWN),
            Map.entry("frost_snap", ArmPose.THROWN),
            Map.entry("glow_crystal", ArmPose.HELD_OUT),
            Map.entry("glow_lux", ArmPose.HELD_UP),
            Map.entry("glow_radiant", ArmPose.HELD_UP),
            Map.entry("glow_radiant_tap", ArmPose.THROWN),
            Map.entry("glow_reflector", ArmPose.THROWN),
            Map.entry("glow_sunbeam", ArmPose.HELD_OUT),
            Map.entry("hex_charm", ArmPose.THROWN),
            Map.entry("hex_drain", ArmPose.HELD_OUT),
            Map.entry("hex_enchant", ArmPose.HELD_UP),
            Map.entry("hex_fuse", ArmPose.HELD_UP),
            Map.entry("hex_lifetap", ArmPose.HELD_UP),
            Map.entry("jelly_feed", ArmPose.THROWN),
            Map.entry("jelly_gluttony", ArmPose.HELD_UP),
            Map.entry("jelly_heal", ArmPose.HELD_OUT),
            Map.entry("jelly_reserve", ArmPose.HELD_OUT),
            Map.entry("kinetic_grab", ArmPose.HELD_OUT),
            Map.entry("kinetic_telekinesis", ArmPose.HELD_UP),
            Map.entry("leaf_barkskin", ArmPose.HELD_UP),
            Map.entry("leaf_bio", ArmPose.THROWN),
            Map.entry("leaf_bloom", ArmPose.THROWN),
            Map.entry("leaf_growth", ArmPose.HELD_OUT),
            Map.entry("leaf_growth_tap", ArmPose.THROWN),
            Map.entry("leaf_reap", ArmPose.THROWN),
            Map.entry("leaf_verdant", ArmPose.THROWN),
            Map.entry("leaf_vines", ArmPose.THROWN),
            Map.entry("metal_javelin", ArmPose.THROWN),
            Map.entry("metal_magnet", ArmPose.HELD_UP),
            Map.entry("metal_spikes", ArmPose.THROWN),
            Map.entry("nether_black_hole", ArmPose.THROWN),
            Map.entry("nether_decay", ArmPose.HELD_OUT),
            Map.entry("nether_decay_tap", ArmPose.THROWN),
            Map.entry("nether_hive", ArmPose.THROWN),
            Map.entry("nether_undead", ArmPose.HELD_UP),
            Map.entry("pulse_extender", ArmPose.HELD_UP),
            Map.entry("pulse_metronome", ArmPose.THROWN),
            Map.entry("pulse_pulser", ArmPose.HELD_OUT),
            Map.entry("pulse_pulser_tap", ArmPose.THROWN),
            Map.entry("pulse_relay", ArmPose.THROWN),
            Map.entry("pulse_zap", ArmPose.HELD_OUT),
            Map.entry("quantum_anchor", ArmPose.THROWN),
            Map.entry("quantum_hole", ArmPose.THROWN),
            Map.entry("quantum_link", ArmPose.THROWN),
            Map.entry("quantum_phase", ArmPose.HELD_UP),
            Map.entry("quantum_phase_shift", ArmPose.THROWN),
            Map.entry("quantum_swap", ArmPose.HELD_OUT),
            Map.entry("rock_bore", ArmPose.HELD_OUT),
            Map.entry("rock_flatten", ArmPose.HELD_OUT),
            Map.entry("rock_petrify", ArmPose.HELD_OUT),
            Map.entry("rock_petrify_tap", ArmPose.THROWN),
            Map.entry("rock_spire", ArmPose.THROWN),
            Map.entry("rock_stoneskin", ArmPose.HELD_UP),
            Map.entry("shroom_colonize", ArmPose.THROWN),
            Map.entry("shroom_fungal_shift", ArmPose.HELD_OUT),
            Map.entry("shroom_mycosis", ArmPose.HELD_OUT),
            Map.entry("shroom_mycosis_tap", ArmPose.THROWN),
            Map.entry("shroom_sight", ArmPose.HELD_UP),
            Map.entry("typhoon_airborn", ArmPose.HELD_UP),
            Map.entry("typhoon_float", ArmPose.THROWN),
            Map.entry("typhoon_gust", ArmPose.HELD_OUT),
            Map.entry("typhoon_lift", ArmPose.THROWN),
            Map.entry("typhoon_tailwind", ArmPose.HELD_OUT),
            Map.entry("typhoon_updraft", ArmPose.THROWN),
            Map.entry("unstable_charged", ArmPose.HELD_UP),
            Map.entry("unstable_explode", ArmPose.THROWN),
            Map.entry("unstable_lurker", ArmPose.THROWN),
            Map.entry("unstable_timed_bomb", ArmPose.THROWN),
            Map.entry("unstable_unmake", ArmPose.HELD_OUT),
            Map.entry("unstable_unmake_tap", ArmPose.THROWN),
            Map.entry("weird_bounce", ArmPose.THROWN),
            Map.entry("weird_magma", ArmPose.THROWN),
            Map.entry("weird_slime", ArmPose.THROWN),
            Map.entry("weird_wobble", ArmPose.THROWN),
            Map.entry("xeno_eldritch", ArmPose.HELD_UP),
            Map.entry("xeno_mutate", ArmPose.THROWN),
            Map.entry("yore_age", ArmPose.THROWN),
            Map.entry("yore_ancient", ArmPose.HELD_UP),
            Map.entry("yore_expire", ArmPose.THROWN),
            Map.entry("yore_rewind", ArmPose.HELD_OUT),
            Map.entry("zoo_allure", ArmPose.THROWN),
            Map.entry("zoo_rally", ArmPose.HELD_UP),
            Map.entry("zoo_shape", ArmPose.THROWN),
            Map.entry("zoo_spawn", ArmPose.THROWN));

    static Stream<Path> shippedAbilities() {
        return AbilityJson.files().stream();
    }

    @ParameterizedTest
    @MethodSource("shippedAbilities")
    void eachShippedAbilityResolvesItsRuledPose(Path file) {
        AbilityDefinition ability = AbilityJson.decode(file);
        assertEquals(RULED.get(ability.id().getPath()), ability.pose(), ability.id().toString());
    }

    @Test
    void theTableRulesEveryShippedAbilityAndNoOther() {
        List<String> shipped = AbilityJson.files().stream()
                .map(file -> AbilityJson.idOf(file.getFileName().toString()).getPath()).sorted().toList();
        assertEquals(RULED.keySet().stream().sorted().toList(), shipped);
    }
}
