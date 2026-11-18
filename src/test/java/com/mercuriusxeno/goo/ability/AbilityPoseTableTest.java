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
            Map.entry("astral_lunar", ArmPose.HELD_OUT),
            Map.entry("astral_meteo", ArmPose.RESTING),
            Map.entry("astral_scry", ArmPose.HELD_UP),
            Map.entry("blaze_flames", ArmPose.HELD_OUT),
            Map.entry("blaze_ignite", ArmPose.SWUNG),
            Map.entry("blaze_kindle", ArmPose.HELD_UP),
            Map.entry("blaze_molten", ArmPose.HELD_OUT),
            Map.entry("crystal_shatter", ArmPose.HELD_OUT),
            Map.entry("crystal_glimmer", ArmPose.HELD_UP),
            Map.entry("crystal_prism", ArmPose.RESTING),
            Map.entry("crystal_scales", ArmPose.HELD_UP),
            Map.entry("crystal_knives", ArmPose.RESTING),
            Map.entry("deep_click", ArmPose.RESTING),
            Map.entry("deep_host", ArmPose.HELD_OUT),
            Map.entry("frost_rime", ArmPose.HELD_OUT),
            Map.entry("deep_shriek", ArmPose.SWUNG),
            Map.entry("deep_sculker", ArmPose.HELD_UP),
            Map.entry("ender_blink", ArmPose.HELD_OUT),
            Map.entry("ender_end", ArmPose.HELD_OUT),
            Map.entry("ender_elude", ArmPose.HELD_UP),
            Map.entry("ender_seal", ArmPose.SWUNG),
            Map.entry("frost_cold", ArmPose.HELD_OUT),
            Map.entry("frost_nova", ArmPose.HELD_UP),
            Map.entry("frost_orb", ArmPose.SWUNG),
            Map.entry("glow_bulb", ArmPose.HELD_OUT),
            Map.entry("glow_lux", ArmPose.HELD_UP),
            Map.entry("glow_wisps", ArmPose.HELD_OUT),
            Map.entry("glow_laser", ArmPose.HELD_OUT),
            Map.entry("hex_charm", ArmPose.RESTING),
            Map.entry("hex_drain", ArmPose.HELD_OUT),
            Map.entry("hex_enchant", ArmPose.HELD_UP),
            Map.entry("hex_fuse", ArmPose.HELD_UP),
            Map.entry("hex_leech", ArmPose.HELD_OUT),
            Map.entry("jelly_nectar", ArmPose.HELD_OUT),
            Map.entry("jelly_gluttony", ArmPose.HELD_UP),
            Map.entry("jelly_heal", ArmPose.HELD_OUT),
            Map.entry("jelly_reserve", ArmPose.HELD_OUT),
            Map.entry("kinetic_grab", ArmPose.HELD_OUT),
            Map.entry("kinetic_slick", ArmPose.HELD_OUT),
            Map.entry("kinetic_remote", ArmPose.HELD_UP),
            Map.entry("leaf_barkskin", ArmPose.HELD_UP),
            Map.entry("leaf_bio", ArmPose.SWUNG),
            Map.entry("leaf_bloom", ArmPose.SWUNG),
            Map.entry("leaf_growth", ArmPose.HELD_OUT),
            Map.entry("leaf_reap", ArmPose.HELD_OUT),
            Map.entry("leaf_vines", ArmPose.HELD_OUT),
            Map.entry("metal_dart", ArmPose.SWUNG),
            Map.entry("metal_magnet", ArmPose.HELD_UP),
            Map.entry("metal_spikes", ArmPose.HELD_OUT),
            Map.entry("nether_decay", ArmPose.HELD_OUT),
            Map.entry("nether_infest", ArmPose.HELD_OUT),
            Map.entry("nether_tartarus", ArmPose.HELD_OUT),
            Map.entry("nether_undead", ArmPose.HELD_UP),
            Map.entry("pulse_signal", ArmPose.HELD_OUT),
            Map.entry("pulse_tick", ArmPose.HELD_OUT),
            Map.entry("pulse_zap", ArmPose.HELD_OUT),
            Map.entry("quantum_tunnel", ArmPose.SWUNG),
            Map.entry("quantum_portal", ArmPose.HELD_OUT),
            Map.entry("quantum_phase", ArmPose.HELD_OUT),
            Map.entry("rock_bore", ArmPose.HELD_OUT),
            Map.entry("rock_level", ArmPose.HELD_OUT),
            Map.entry("rock_petrify", ArmPose.HELD_OUT),
            Map.entry("rock_build", ArmPose.RESTING),
            Map.entry("rock_stoneskin", ArmPose.HELD_UP),
            Map.entry("rock_gouge", ArmPose.HELD_OUT),
            Map.entry("shroom_spore", ArmPose.SWUNG),
            Map.entry("shroom_transmit", ArmPose.HELD_OUT),
            Map.entry("shroom_mycosis", ArmPose.HELD_OUT),
            Map.entry("shroom_sight", ArmPose.HELD_UP),
            Map.entry("typhoon_float", ArmPose.HELD_UP),
            Map.entry("typhoon_gust", ArmPose.HELD_OUT),
            Map.entry("typhoon_pneuma", ArmPose.HELD_UP),
            Map.entry("typhoon_shulk", ArmPose.RESTING),
            Map.entry("typhoon_updraft", ArmPose.HELD_OUT),
            Map.entry("unstable_blast", ArmPose.RESTING),
            Map.entry("unstable_nitro", ArmPose.HELD_OUT),
            Map.entry("void_cancel", ArmPose.SWUNG),
            Map.entry("void_zone", ArmPose.HELD_OUT),
            Map.entry("void_hole", ArmPose.RESTING),
            Map.entry("void_vacuum", ArmPose.HELD_OUT),
            Map.entry("weird_goop", ArmPose.HELD_OUT),
            Map.entry("weird_mitosis", ArmPose.SWUNG),
            Map.entry("xeno_unveil", ArmPose.HELD_UP),
            Map.entry("yore_age", ArmPose.SWUNG),
            Map.entry("yore_ancient", ArmPose.HELD_UP),
            Map.entry("yore_expire", ArmPose.SWUNG),
            Map.entry("yore_rewind", ArmPose.RESTING),
            Map.entry("zoo_allure", ArmPose.SWUNG),
            Map.entry("yarn_herd", ArmPose.HELD_UP),
            Map.entry("yarn_lead", ArmPose.RESTING),
            Map.entry("zoo_shape", ArmPose.RESTING),
            Map.entry("zoo_spawn", ArmPose.RESTING));

    static Stream<Path> shippedAbilities() {
        return AbilityJson.files().stream();
    }

    @ParameterizedTest
    @MethodSource("shippedAbilities")
    void eachShippedAbilityResolvesItsRuledPose(Path file) {
        AbilityDefinition ability = AbilityJson.decode(file);
        assertEquals(RULED.get(ability.id().getPath()), ability.pose(), ability.id().toString());
    }

    /**
     * Knives holds and throws with the hand where vanilla draws it, at the
     * glove's side: its hold and release frames are both the frame that moves
     * the hand nowhere, so its across-the-chest share stands at zero through
     * the hold and every throw (decision knives-hand-stays-at-its-side-off-the-screen-center).
     */
    @Test
    void knivesHoldsAndThrowsWithTheHandAtItsSide() {
        ArmPose knives = AbilityJson.decode("crystal_knives").pose();
        assertEquals(ArmPoseKind.NONE, knives.holdFrame());
        assertEquals(ArmPoseKind.NONE, knives.releaseFrame());
    }

    @Test
    void theTableRulesEveryShippedAbilityAndNoOther() {
        List<String> shipped = AbilityJson.files().stream()
                .map(file -> AbilityJson.idOf(file.getFileName().toString()).getPath()).sorted().toList();
        assertEquals(RULED.keySet().stream().sorted().toList(), shipped);
    }
}
