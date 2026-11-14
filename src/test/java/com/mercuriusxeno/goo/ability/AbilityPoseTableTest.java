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
            Map.entry("astral_meteo", ArmPose.SWUNG),
            Map.entry("astral_scry", ArmPose.HELD_UP),
            Map.entry("blaze_flames", ArmPose.HELD_OUT),
            Map.entry("blaze_ignite", ArmPose.SWUNG),
            Map.entry("blaze_kindle", ArmPose.HELD_UP),
            Map.entry("blaze_melt", ArmPose.SWUNG),
            Map.entry("crystal_cloud", ArmPose.SWUNG),
            Map.entry("crystal_glitter", ArmPose.HELD_UP),
            Map.entry("crystal_prism", ArmPose.SWUNG),
            Map.entry("crystal_scales", ArmPose.HELD_UP),
            Map.entry("crystal_knives", ArmPose.SHOULDER_CAST),
            Map.entry("deep_click", ArmPose.SWUNG),
            Map.entry("deep_shriek", ArmPose.SWUNG),
            Map.entry("deep_sculker", ArmPose.HELD_UP),
            Map.entry("ender_blink", ArmPose.HELD_OUT),
            Map.entry("ender_end", ArmPose.SWUNG),
            Map.entry("ender_elude", ArmPose.HELD_UP),
            Map.entry("ender_seal", ArmPose.SWUNG),
            Map.entry("frost_cold", ArmPose.HELD_OUT),
            Map.entry("frost_iceborn", ArmPose.HELD_UP),
            Map.entry("frost_nova", ArmPose.HELD_UP),
            Map.entry("frost_orb", ArmPose.SWUNG),
            Map.entry("glow_bulb", ArmPose.HELD_OUT),
            Map.entry("glow_lux", ArmPose.HELD_UP),
            Map.entry("glow_wisps", ArmPose.HELD_UP),
            Map.entry("glow_laser", ArmPose.HELD_OUT),
            Map.entry("hex_charm", ArmPose.SWUNG),
            Map.entry("hex_drain", ArmPose.HELD_OUT),
            Map.entry("hex_enchant", ArmPose.HELD_UP),
            Map.entry("hex_fuse", ArmPose.HELD_UP),
            Map.entry("hex_lifetap", ArmPose.HELD_UP),
            Map.entry("jelly_feed", ArmPose.SWUNG),
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
            Map.entry("leaf_vines", ArmPose.SWUNG),
            Map.entry("metal_dart", ArmPose.SWUNG),
            Map.entry("metal_magnet", ArmPose.HELD_UP),
            Map.entry("metal_urchin", ArmPose.SWUNG),
            Map.entry("nether_decay", ArmPose.HELD_OUT),
            Map.entry("nether_undead", ArmPose.HELD_UP),
            Map.entry("pulse_extend", ArmPose.HELD_UP),
            Map.entry("pulse_tick", ArmPose.HELD_OUT),
            Map.entry("pulse_zap", ArmPose.HELD_OUT),
            Map.entry("quantum_tunnel", ArmPose.SWUNG),
            Map.entry("quantum_portal", ArmPose.SWUNG),
            Map.entry("quantum_phase_self", ArmPose.HELD_UP),
            Map.entry("quantum_phase_other", ArmPose.SWUNG),
            Map.entry("rock_bore", ArmPose.HELD_OUT),
            Map.entry("rock_level", ArmPose.HELD_OUT),
            Map.entry("rock_petrify", ArmPose.HELD_OUT),
            Map.entry("rock_spire", ArmPose.SWUNG),
            Map.entry("rock_stoneskin", ArmPose.HELD_UP),
            Map.entry("rock_gouge", ArmPose.HELD_OUT),
            Map.entry("shroom_spore", ArmPose.SWUNG),
            Map.entry("shroom_transmit", ArmPose.HELD_OUT),
            Map.entry("shroom_mycosis", ArmPose.HELD_OUT),
            Map.entry("shroom_sight", ArmPose.HELD_UP),
            Map.entry("typhoon_float", ArmPose.HELD_UP),
            Map.entry("typhoon_gust", ArmPose.HELD_OUT),
            Map.entry("typhoon_pneuma", ArmPose.HELD_UP),
            Map.entry("typhoon_shulk", ArmPose.SWUNG),
            Map.entry("typhoon_updraft", ArmPose.SWUNG),
            Map.entry("unstable_blast", ArmPose.SWUNG),
            Map.entry("unstable_lurker", ArmPose.SWUNG),
            Map.entry("void_erase", ArmPose.SWUNG),
            Map.entry("void_zone", ArmPose.HELD_OUT),
            Map.entry("void_hole", ArmPose.SWUNG),
            Map.entry("void_vacuum", ArmPose.HELD_OUT),
            Map.entry("weird_goop", ArmPose.HELD_OUT),
            Map.entry("weird_mitosis", ArmPose.SWUNG),
            Map.entry("xeno_unveil", ArmPose.HELD_UP),
            Map.entry("yore_age", ArmPose.SWUNG),
            Map.entry("yore_ancient", ArmPose.HELD_UP),
            Map.entry("yore_expire", ArmPose.SWUNG),
            Map.entry("yore_rewind", ArmPose.HELD_OUT),
            Map.entry("zoo_allure", ArmPose.SWUNG),
            Map.entry("zoo_leader", ArmPose.HELD_UP),
            Map.entry("zoo_rally", ArmPose.SWUNG),
            Map.entry("zoo_shape", ArmPose.SWUNG),
            Map.entry("zoo_spawn", ArmPose.SWUNG));

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
