package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.network.AttackTouchTests;
import com.mercuriusxeno.goo.network.BarkskinTests;
import com.mercuriusxeno.goo.network.BlockLandingTests;
import com.mercuriusxeno.goo.network.BrewEffectTests;
import com.mercuriusxeno.goo.network.ColonizeTests;
import com.mercuriusxeno.goo.network.FungalShiftTests;
import com.mercuriusxeno.goo.network.GloveSelectTests;
import com.mercuriusxeno.goo.network.HeartOverlayTests;
import com.mercuriusxeno.goo.network.MobEffectTests;
import com.mercuriusxeno.goo.network.MycosisTests;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.network.StreamDeliveryTests;
import com.mercuriusxeno.goo.network.TouchDeliveryTests;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Registers goo gametest functions via NeoForge's RegisterEvent.
 * TestFunctionLoader.runLoaders() fires during Bootstrap.bootStrap(),
 * before mod loading, so we use RegisterEvent instead to register
 * into the TEST_FUNCTION registry at the correct time. The class lives in
 * the gametest source set, which only the gameTestServer run loads, so a
 * shipped jar registers nothing (decision gametest-and-tools-source-sets).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooTestFunctions {

    // --- Smoke ---
    private static final String SMOKE = "smoke";

    // --- Goo type registry ---
    private static final String TYPES_BUNDLED_RESOLVE = "types_bundled_resolve";
    private static final String TYPES_DATAPACK_LISTED = "types_datapack_listed";
    private static final String TYPES_MARKER_RELOADS = "types_marker_reloads";
    private static final String TYPES_GLOVE_RELOADS = "types_glove_reloads";

    // --- Goo value lifecycle ---
    private static final String VALUES_FRESH_AFTER_STOP = "values_fresh_after_stop";
    private static final String GLOVE_TYPE_ONLY_REFUSED = "glove_type_only_refused";
    private static final String GLOVE_GATED_SELECTION_REFUSED = "glove_gated_selection_refused";
    private static final String GLOVE_SHIFT_RECOLLECTS_MARKER = "glove_shift_recollects_marker";
    private static final String GLOVE_CLICK_NO_USING_STATE = "glove_click_no_using_state";
    private static final String GLOVE_FIRST_SOURCE_DEPLETES_FIRST = "glove_first_source_depletes_first";

    // --- Generic goo fluid ---
    private static final String FLUID_TYPES_SIDE_BY_SIDE = "fluid_types_side_by_side";
    private static final String FLUID_FIELDS_STAMPED = "fluid_fields_stamped";

    // --- Generic goo items ---
    private static final String ITEM_THROWN_GOO_OWN_TYPE = "item_thrown_goo_own_type";
    private static final String ITEM_TAB_DATAPACK_TYPE = "item_tab_datapack_type";

    // --- Exorite tier ---
    private static final String EXORITE_TEMPLATE_REGISTERED = "exorite_template_registered";
    private static final String EXORITE_TEMPLATE_IN_ANCIENT_CITY_LOOT = "exorite_template_in_ancient_city_loot";
    private static final String EXORITE_TEMPLATE_DUPLICATES = "exorite_template_duplicates";
    private static final String EXORITE_SET_REGISTERED = "exorite_set_registered";
    private static final String EXORITE_SET_SMITHING = "exorite_set_smithing";
    private static final String EXORITE_SET_REPAIRS = "exorite_set_repairs";
    private static final String EXORITE_ARMOR_STATS = "exorite_armor_stats";
    private static final String EXORITE_SURVIVES_ZERO_DURABILITY = "exorite_survives_zero_durability";
    private static final String EXORITE_BROKEN_ACTS_AS_HAND = "exorite_broken_acts_as_hand";
    private static final String EXORITE_BROKEN_TOOLTIP = "exorite_broken_tooltip";
    private static final String EXORITE_ANVIL_REPAIR = "exorite_anvil_repair";
    private static final String SOUL_BOUND_TAG_HOLDS_EXORITE = "soul_bound_tag_holds_exorite";
    private static final String SOUL_BOUND_SURVIVES_DEATH = "soul_bound_survives_death";
    private static final String KNOWN_ITEMS_SURVIVE_DEATH = "known_items_survive_death";
    private static final String EXO_GAUNTLET_SMITHING = "exo_gauntlet_smithing";
    private static final String EXO_GAUNTLET_KEEPS_BENEFITS = "exo_gauntlet_keeps_benefits";
    // --- Crystallizer ---
    private static final String CRYSTALLIZER_CRYSTAL_FIRST = "crystallizer_crystal_first_then_ender";
    private static final String CRYSTALLIZER_EITHER_SLOT = "crystallizer_either_slot_holds_crystal";
    private static final String CRYSTALLIZER_TWO_CRYSTAL = "crystallizer_two_crystal_grow_crystal";
    private static final String CRYSTALLIZER_TAKES_ONLY_TWO_SLOTS = "crystallizer_takes_only_two_slots";
    private static final String CRYSTALLIZER_CANISTERS_AT_ITS_CENTERS = "crystallizer_canisters_at_its_centers";
    private static final String CRYSTALLIZER_ADVANCES_TO_BUDDING_CHRYSM = "crystallizer_advances_to_budding_chrysm";
    private static final String CRYSTALLIZER_SMALL_DIAL_HOLDS = "crystallizer_small_dial_holds";
    private static final String CRYSTALLIZER_DIAL_WRAPS = "crystallizer_dial_wraps";
    private static final String CRYSTALLIZER_PAUSES_WITHOUT_CRYSTAL = "crystallizer_pauses_without_crystal";
    private static final String CRYSTALLIZER_PART_GROWN_NOT_CLICKABLE = "crystallizer_part_grown_not_clickable";
    private static final String CRYSTALLIZER_DIAL_KEEPS_GROWING = "crystallizer_dial_keeps_growing";
    private static final String CRYSTALLIZER_OFF_PAUSES = "crystallizer_off_pauses";
    private static final String CRYSTALLIZER_HANDS_THE_EXCESS = "crystallizer_hands_the_excess";
    private static final String CRYSTALLIZER_ANY_ITEM_TAKES = "crystallizer_any_item_takes";
    private static final String CRYSTALLIZER_PASSES_OTHER_CLICKS = "crystallizer_passes_other_clicks";
    private static final String CRYSTALLIZER_CANISTER_SOUNDS = "crystallizer_canister_sounds";
    private static final String CRYSTALLIZER_POUR_AIMED_ONLY = "crystallizer_pour_aimed_only";
    private static final String CRYSTALLIZER_ONE_GROWING_GOO = "crystallizer_one_growing_goo";
    private static final String CRYSTALLIZER_GASKET_FILLS_CANISTER = "crystallizer_gasket_fills_canister";
    private static final String CRYSTALLIZER_CLICK_CRYSTAL = "crystallizer_click_crystal";
    private static final String CRYSTALLIZER_EVEN_PACE = "crystallizer_even_pace";
    private static final String CRYSTALLIZER_STANDING_AIM_TAKES = "crystallizer_standing_aim_takes_";
    private static final String CRYSTALLIZER_OFF_CRYSTALLIZES_NOTHING = "crystallizer_off_crystallizes_nothing";
    // --- Gasket demand ---
    private static final String GASKET_DEMAND_VAT_FILLS_HUB = "gasket_demand_vat_fills_hub";
    private static final String GASKET_DEMAND_VAT_FEEDS_TAP = "gasket_demand_vat_feeds_tap";
    private static final String GASKET_DEMAND_TAP_CANISTER_FIRST = "gasket_demand_tap_canister_first";
    private static final String GASKET_DEMAND_TAP_TYPES_BY_TURNS = "gasket_demand_tap_types_by_turns";
    private static final String GASKET_DEMAND_CRYSTALLIZER_CHAIN = "gasket_demand_crystallizer_chain";
    private static final String GASKET_DEMAND_CRYSTALLIZER_VAT_CHAIN = "gasket_demand_crystallizer_vat_chain";
    private static final String GASKET_DEMAND_HUB_SHARES = "gasket_demand_hub_shares";
    // --- Brewing ---
    private static final String BREWING_GOO_NEVER_BREWS = "brewing_goo_never_brews";
    private static final String BREWING_CHRYSM_BREWS_POTION = "brewing_chrysm_brews_potion";
    private static final String BREWING_CHRYSM_BREWS_SPLASH = "brewing_chrysm_brews_splash";
    private static final String BREWING_CHRYSM_BREWS_LINGERING = "brewing_chrysm_brews_lingering";
    private static final String BREWING_HIGHER_TIERS_NEVER_BREW = "brewing_higher_tiers_never_brew";
    private static final String EXORITE_NOT_ENCHANTABLE = "exorite_not_enchantable";
    private static final String EXORITE_ANVIL_REFUSES_BOOK = "exorite_anvil_refuses_book";
    private static final String EXORITE_BARS_REGISTERED = "exorite_bars_registered";
    private static final String EXORITE_BARS_CRAFTED = "exorite_bars_crafted";
    private static final String EXORITE_BARS_STRENGTH = "exorite_bars_strength";
    private static final String EXORITE_BARS_DROPS = "exorite_bars_drops";
    private static final String SPAWNER_CRAFTED_FROM_EXORITE_BARS = "spawner_crafted_from_exorite_bars";
    private static final String EMPTY_SPAWNER_TAKES_EGG = "empty_spawner_takes_egg";

    // --- Goo Lab ---
    private static final String LAB_BUILD_SHELL = "lab_build_shell";
    private static final String LAB_TEMPLATE_LOADS = "lab_template_loads";
    private static final String LAB_BAY_TAP = "lab_bay_tap";
    private static final String LAB_BAY_HUB = "lab_bay_hub";
    private static final String LAB_BAY_GASKET = "lab_bay_gasket";
    private static final String LAB_PENS_AND_RANGE = "lab_pens_and_range";
    private static final String LAB_SUPPLY_ROW = "lab_supply_row";
    private static final String LAB_KIT = "lab_kit";
    private static final String LAB_REBUILD = "lab_rebuild";
    private static final String ZOMBIE_PEN_HOLDS_ONLY_ZOMBIES = "zombie_pen_holds_only_zombies";
    private static final String PASSIVE_PEN_HAS_NO_ROOF = "passive_pen_has_no_roof";
    private static final String LAB_KIT_TEACHES_EVERY_ABILITY = "lab_kit_teaches_every_ability";
    private static final String LAB_KIT_UNTOUCHED_PLAYER_KNOWS_NOTHING = "lab_kit_untouched_player_knows_nothing";

    // --- GasketPusher ---
    private static final String PUSHER_EMPTY_RESERVOIR = "pusher_empty_reservoir";
    private static final String PUSHER_NO_PARTNER = "pusher_no_partner";
    private static final String PUSHER_DISPOSE_DROPS_GASKET = "pusher_dispose_drops_gasket_once";
    private static final String PUSHER_DOUBLE_DISPOSE_DROPS_GASKETS = "pusher_double_dispose_drops_gasket_each_time";
    private static final String PUSHER_REACTOR_OUTPUT_PUSH = "pusher_reactor_output_push";
    private static final String PUSHER_REACTOR_OUTPUT_REMOVAL = "pusher_reactor_output_removal";
    private static final String PUSHER_WATERLOGGED_GASKET_VAT = "pusher_waterlogged_gasket_vat";
    private static final String PUSHER_BREAK_RELEASES_CHUNK = "pusher_crucible_break_releases_partner_chunk";
    private static final String PUSHER_UNLOAD_RELEASES_CHUNK = "pusher_crucible_unload_releases_partner_chunk";
    private static final String PUSHER_MOVED_RECEIVER = "pusher_moved_receiver_keeps_receiving";

    // --- IGasketHolder ---
    private static final String CRUCIBLE_ROLE_TRANSMITTER = "crucible_role_transmitter";
    private static final String CRUCIBLE_NO_GASKET = "crucible_no_gasket";
    private static final String CRUCIBLE_WITH_GASKET = "crucible_with_gasket";
    private static final String VAT_SUPPORTS_ROLE = "vat_supports_role";
    private static final String HUB_HAS_INTAKE = "hub_has_intake";
    private static final String DEFAULT_ALLOWS_TUNING = "default_allows_tuning";
    private static final String REACTOR_GASKET_INSTALL = "reactor_gasket_install";
    private static final String REACTOR_TUNER_LINK = "reactor_tuner_link";
    private static final String CRUCIBLE_GOO_GASKET_TUNER = "crucible_goo_gasket_tuner";
    private static final String REACTOR_GASKET_LOCATION = "reactor_gasket_location";
    private static final String REACTOR_SEATED_GASKET_METADATA = "reactor_seated_gasket_metadata";
    private static final String TAP_ROLE_RECEIVER = "tap_role_receiver";
    private static final String TAP_TUNER_LINK = "tap_tuner_link";
    private static final String TAP_REFUSES_TRANSMITTER = "tap_refuses_transmitter";
    private static final String HUB_SLOT_TRANSMITS = "hub_slot_transmits";
    private static final String HUB_SUPPORTS_BOTH_ROLES = "hub_supports_both_roles";
    private static final String HUB_SLOT_MISS_INTAKE = "hub_slot_miss_intake";
    private static final String TAP_ATTACHMENT_LOADS = "tap_attachment_loads";
    private static final String HUB_SLOT_GASKET_REGISTERED = "hub_slot_gasket_registered";
    private static final String BREAK_POPS_GASKET_CRUCIBLE = "break_pops_gasket_crucible";
    private static final String BREAK_POPS_GASKET_VAT = "break_pops_gasket_vat";
    private static final String BREAK_POPS_GASKET_TAP = "break_pops_gasket_tap";
    private static final String BREAK_POPS_GASKET_HUB = "break_pops_gasket_hub";
    private static final String REMOVAL_POPS_GASKET_EVERY_MACHINE = "removal_pops_gasket_every_machine";
    private static final String BREAK_RELEASES_STANDING_GASKET = "break_releases_standing_gasket";
    private static final String PLACED_HUB_REGISTERS_CANISTER = "placed_hub_registers_canister";

    // --- Sneak empty-hand gasket removal ---
    private static final String SNEAK_POPS_CANISTER_SLOT_GASKET = "sneak_pops_canister_slot_gasket";
    private static final String SNEAK_POPS_HUB_GASKET = "sneak_pops_hub_gasket";
    private static final String SNEAK_POPS_TAP_GASKET = "sneak_pops_tap_gasket";
    private static final String SNEAK_POPS_VAT_GASKET = "sneak_pops_vat_gasket";
    private static final String SNEAK_POPS_REACTOR_GASKET = "sneak_pops_reactor_gasket";
    private static final String SNEAK_POPS_CRUCIBLE_GASKET = "sneak_pops_crucible_gasket";

    // --- Effect executors ---
    private static final String FX_METAL = "fx_metal_runs";
    private static final String FX_CRYSTAL = "fx_crystal_runs";
    private static final String FX_NETHER = "fx_nether_implodes";
    private static final String FX_UNSTABLE = "fx_unstable_explodes";
    private static final String FX_PROGRAM_GLOW_WALL = "fx_program_glow_wall";
    private static final String FX_PROGRAM_GLOW_FLOOR = "fx_program_glow_floor";
    private static final String FX_FALLEN_MARKER_KEEPS_ABILITY = "fx_fallen_marker_keeps_ability";
    private static final String FX_NO_ABILITY_LANDS_NOTHING = "fx_no_ability_lands_nothing";
    private static final String FX_ABILITY_LANDS_MARKER = "fx_ability_lands_marker";
    private static final String FX_BLAST_LANDS_NO_BLOCK = "fx_blast_lands_no_block";
    private static final String FREE_BLAST_AT_AIR_POINT = "free_blast_at_air_point";
    private static final String FREE_BLAST_AT_SKY = "free_blast_at_sky";
    private static final String BLAST_CRATER_INSIDE_SPHERE = "blast_crater_inside_sphere";
    private static final String BLAST_DROPS_EVERY_DIRT = "blast_drops_every_dirt";
    private static final String BLAST_SPARES_ITEMS = "blast_spares_items";
    private static final String FX_CLOUD_BLOCK_GOES = "fx_cloud_block_goes";
    private static final String FX_TRAP_BLOCK_GOES = "fx_trap_block_goes";
    private static final String FX_CRYSTAL_GROWS = "fx_crystal_grows";
    private static final String FX_OTHER_ABILITY_MARKS_CRYSTAL = "fx_other_ability_marks_crystal";
    private static final String FX_PROGRAM_INSTANT = "fx_program_instant_detonation";
    private static final String FX_PROGRAM_TIMED = "fx_program_timed_bomb";
    private static final String FX_PROGRAM_MINE = "fx_program_proximity_mine";
    private static final String FX_PROGRAM_METAL_SPIKES = "fx_program_metal_spikes";
    private static final String FX_PROGRAM_CRYSTAL_CLOUD = "fx_program_crystal_cloud";
    private static final String FX_PROGRAM_NETHER_BLACK_HOLE = "fx_program_nether_black_hole";
    private static final String FX_LANDED_CRYSTAL_CLOUD = "fx_landed_crystal_cloud_live";
    private static final String FX_LANDED_METAL_SPIKES = "fx_landed_metal_spikes_live";
    private static final String FX_LANDED_BLACK_HOLE = "fx_landed_black_hole_gathers";
    private static final String FX_LANDED_BLAST = "fx_landed_blast_explodes";
    private static final String FX_LANDED_MINE = "fx_landed_mine_awaits";
    private static final String FX_LANDED_GLOW_CRYSTAL = "fx_landed_glow_crystal_stands";

    // --- Crucible ---
    private static final String CR_GOO_INSERT = "cr_goo_insert";
    private static final String CR_ITEM_ABSORB = "cr_item_absorb";
    private static final String CR_THROWN_ITEM_TEACHES = "cr_thrown_item_teaches";
    private static final String CR_THROWN_CONTAINER_TEACHES = "cr_thrown_container_teaches";
    private static final String CR_UNTHROWN_ITEM_TEACHES_NOBODY = "cr_unthrown_item_teaches_nobody";
    private static final String CR_MELTS_CHRYSM = "cr_melts_chrysm";
    private static final String CR_CAP_EACH_TYPE = "cr_cap_each_type";
    private static final String CR_CAP_GOO_IN_HAND = "cr_cap_goo_in_hand";
    private static final String CR_CAP_GOO_ENTITY = "cr_cap_goo_entity";
    private static final String CR_CAP_ITEMS_THAT_FIT = "cr_cap_items_that_fit";
    private static final String CR_CAP_CONTAINER = "cr_cap_container";
    private static final String CR_CAP_MELTED_ITEM = "cr_cap_melted_item";
    private static final String CR_CAP_ACCOUNTED = "cr_cap_accounted";
    private static final String CR_GOO_STACK_WHOLE = "cr_goo_stack_whole";
    private static final String CR_GOO_STACK_TO_CAP = "cr_goo_stack_to_cap";
    private static final String CR_FIRST_MELT_PUDDLE = "cr_first_melt_puddle";
    private static final String CR_DROP_RESTS_ON_FLOOR = "cr_drop_rests_on_floor";
    private static final String CR_LEDGE_ITEM_TAKEN_INSIDE = "cr_ledge_item_taken_inside";
    private static final String CR_WALL_TOP_ITEMS_SLIDE_IN = "cr_wall_top_items_slide_in";
    private static final String CR_LEDGE_ITEM_LIFTED_IN = "cr_ledge_item_lifted_in";
    private static final String CR_THROWN_ITEM_CAUGHT = "cr_thrown_item_caught";
    private static final String CR_SIDE_HIT_LIFTED_IN = "cr_side_hit_lifted_in";
    private static final String CR_ROUGH_THROW_STEERED_IN = "cr_rough_throw_steered_in";
    private static final String CR_ITEM_TAKEN_AT_KILL_BOX = "cr_item_taken_at_kill_box";
    private static final String CR_COLD_ITEM_WAITS_ON_FLOOR = "cr_cold_item_waits_on_floor";
    private static final String CR_ITEM_MELTS_ON_ITS_CLOCK = "cr_item_melts_on_its_clock";
    private static final String CR_BROKEN_MID_MELT_DROPS_REMAINDER = "cr_broken_mid_melt_drops_remainder";
    private static final String CR_STACK_MELTS_ITEM_BY_ITEM = "cr_stack_melts_item_by_item";
    private static final String CR_COMBO_MELTS_EVERY_ITEM = "cr_combo_melts_every_item";
    private static final String CR_SPARK_REFUSED_BLAZE = "cr_spark_refused_blaze";
    private static final String CR_SPARK_REFUSED_HEAT = "cr_spark_refused_heat";
    private static final String CR_SPARK_LIGHTS_COLD = "cr_spark_lights_cold";

    // --- Placement ---
    private static final String PL_DOUBLE_STACK = "pl_double_hit_stacks";
    private static final String PL_SIDEWAYS_NEIGHBOR = "pl_sideways_neighbor";
    private static final String PL_OTHER_TYPES = "pl_other_types_place";
    private static final String PL_ABILITY_HIT_BLOCK = "pl_ability_hit_block";
    private static final String PL_ABILITY_WATERLOG = "pl_ability_waterlog";
    private static final String PL_ABILITY_LAVA = "pl_ability_lava";
    private static final String PL_ABILITY_SAME_STACK = "pl_ability_same_stack";
    private static final String PL_OTHER_ABILITY_THROW_LEAVES_MARKER = "pl_other_ability_throw_leaves_marker";
    private static final String PL_SECOND_THROW_COSTS_THE_SAME = "pl_second_throw_costs_the_same";
    private static final String PRISM_GROWS_ON_THE_FACE = "prism_grows_on_the_face";
    private static final String PRISM_REFUSED_WITHOUT_QUARTZ = "prism_refused_without_quartz";
    private static final String PRISM_COMBO_RUNS_THE_TYPE_PRISM_ABILITY = "prism_combo_runs_the_type_prism_ability";
    private static final String PRISM_COMBO_RUNS_ON_PRISM_BEHAVIORS = "prism_combo_runs_on_prism_behaviors";
    private static final String PRISM_WITHOUT_COMBO_STAYS = "prism_without_combo_stays";
    private static final String COMBINED_PRISM_REFUSES_SECOND = "combined_prism_refuses_second";

    // --- Canister interactions ---
    private static final String IX_CANISTER_PLAIN_INSERT = "ix_canister_plain_insert";
    private static final String IX_CANISTER_BUCKET_FILL = "ix_canister_bucket_fill";
    private static final String IX_CANISTER_GASKET_INSTALL = "ix_canister_gasket_install";
    private static final String IX_CANISTER_CLICK_PICKUP = "ix_canister_click_pickup";
    private static final String IX_CANISTER_LAST_PICKUP = "ix_canister_last_pickup";
    private static final String IX_CANISTER_EMPTY_HAND = "ix_canister_empty_hand";
    private static final String IX_CANISTER_VAT_TOP_PLACES = "ix_canister_vat_top_places";
    private static final String IX_CANISTER_STONE_TOP_PLACES = "ix_canister_stone_top_places";
    private static final String IX_CANISTER_CRUCIBLE_TOP_REFUSES = "ix_canister_crucible_top_refuses";
    private static final String IX_CANISTER_SIGN_TOP_REFUSES = "ix_canister_sign_top_refuses";
    private static final String IX_CANISTER_SNEAK_VAT_TOP_PLACES = "ix_canister_sneak_vat_top_places";
    private static final String IX_CANISTER_HUB_ATTACH_SPOT = "ix_canister_hub_attach_spot";

    // --- Machine interactions ---
    private static final String IX_TAP_VALVE = "ix_tap_valve_toggle";
    private static final String IX_TAP_TOP_CLICK_INSERT = "ix_tap_top_click_insert";
    private static final String IX_TAP_SLOT_CLICK_INSERT = "ix_tap_slot_click_insert";
    private static final String IX_TAP_EMPTY_HAND_TAKE = "ix_tap_empty_hand_take";
    private static final String IX_TAP_GOO_POUR = "ix_tap_goo_pour";

    // --- Tap drip ---
    private static final String TAP_DRIP_DRAWS_ONE_MB = "tap_drip_draws_one_mb";
    private static final String TAP_VALVE_GATES_DRIP = "tap_valve_gates_drip";
    private static final String TAP_DRIP_LANDS_BELOW = "tap_drip_lands_below";
    private static final String TAP_DRIP_BOTTOMLESS = "tap_drip_bottomless";
    private static final String TAP_HOST_PLACES_ABOVE_LANDING = "tap_host_places_above_landing";
    private static final String TAP_DRIP_NO_ABILITY = "tap_drip_no_ability";
    private static final String TAP_DRIP_SENDS_TAP_DRIP = "tap_drip_sends_tap_drip";
    private static final String TAP_VALVE_STEPS_FIVE_GRADES = "tap_valve_steps_five_grades";
    private static final String TAP_SNEAK_CLICK_STEPS_VALVE_BACK = "tap_sneak_click_steps_valve_back";
    private static final String TAP_DRIP_FILLS_CRUCIBLE_BELOW = "tap_drip_fills_crucible_below";
    private static final String TAP_DRIP_ONE_TO_FOUR_FILLS_CRUCIBLE = "tap_drip_one_to_four_fills_crucible";
    private static final String TAP_DRIP_INTO_CRUCIBLE_RUNS_NO_PROGRAM = "tap_drip_into_crucible_runs_no_program";
    private static final String TAP_DRIP_ON_REFUSING_BLOCK_RUNS_PROGRAM = "tap_drip_on_refusing_block_runs_program";
    private static final String IX_VAT_GASKET = "ix_vat_gasket_apply";
    private static final String IX_HUB_INSERT = "ix_hub_canister_insert";
    private static final String IX_HUB_PICKUP = "ix_hub_canister_pickup";
    private static final String IX_HUB_POUR_AIMED_ONLY = "ix_hub_pour_aimed_only";
    private static final String IX_PLEXER_TARGET = "ix_plexer_set_target";
    private static final String IX_PLEXER_REFUSES_UNLEARNED = "ix_plexer_refuses_unlearned";
    private static final String IX_REACTOR_INSERT_PICKUP = "ix_reactor_insert_pickup";
    private static final String IX_CRUCIBLE_BLAZE_ROD_COLD = "ix_crucible_blaze_rod_click_leaves_cold";
    private static final String IX_CRUCIBLE_COLD_ABSORBS_NOTHING = "ix_crucible_cold_absorbs_nothing";
    private static final String IX_CRUCIBLE_BLAZE_ABSORBS = "ix_crucible_blaze_absorbs_item";
    private static final String IX_CRUCIBLE_FLINT_SPARKS = "ix_crucible_flint_and_steel_sparks";
    private static final String IX_CRUCIBLE_SPARK_MELTS_COAL = "ix_crucible_spark_melts_coal";
    private static final String IX_HUB_ITEM_GOO_INSERT = "ix_hub_item_goo_insert";
    private static final String IX_HUB_ITEM_GOO_INSERT_REMAINDER = "ix_hub_item_goo_insert_remainder";
    private static final String IX_HUB_ITEM_INSERT_REFUSED = "ix_hub_item_insert_refused";
    private static final String IX_HUB_ITEM_DRAINS_NOTHING = "ix_hub_item_drains_nothing";
    private static final String IX_HUB_ITEM_IS_GOO_SOURCE = "ix_hub_item_is_goo_source";
    private static final String IX_VAT_ITEM_GOO_INSERT = "ix_vat_item_goo_insert";
    private static final String IX_VAT_ITEM_GOO_INSERT_REMAINDER = "ix_vat_item_goo_insert_remainder";
    private static final String IX_VAT_ITEM_DRAIN = "ix_vat_item_drain";
    private static final String IX_GOO_INSERT_SHARED = "ix_goo_insert_shared";
    private static final String IX_VAT_STREAM_HOLDS = "ix_vat_stream_holds";
    private static final String IX_CRUCIBLE_TOPS_UP_CANISTER = "ix_crucible_tops_up_canister";
    private static final String IX_VAT_UNPACKS_EVERY_TYPE = "ix_vat_unpacks_every_type";

    // --- Machines ---
    private static final String MACHINE_CANISTER_INSERT = "machine_canister_insert";
    private static final String MACHINE_CANISTER_REMOVE = "machine_canister_remove";
    private static final String MACHINE_CANISTER_TICK = "machine_canister_tick";
    private static final String MACHINE_CANISTER_BREAK_RELEASES_GASKET = "machine_canister_break_releases_gasket";
    private static final String MACHINE_CANISTER_FLUID = "machine_canister_fluid";
    private static final String MACHINE_CANISTER_ROUTING = "machine_canister_routing";
    private static final String MACHINE_CANISTER_ROUNDTRIP = "machine_canister_roundtrip";
    private static final String MACHINE_REACTOR_WITHOUT_INPUTS = "machine_reactor_without_inputs";
    private static final String MACHINE_REACTOR_BREAK_RELEASES_GASKET = "machine_reactor_break_releases_gasket";
    private static final String MACHINE_REACTOR_REACTION = "machine_reactor_reaction";
    private static final String MACHINE_REACTOR_REDSTONE = "machine_reactor_redstone";
    private static final String MACHINE_PLEXER_WITHOUT_GOO = "machine_plexer_without_goo";

    // --- MobEffects ---
    private static final String MOB_METAL = "mob_metal_javelin";
    private static final String MOB_METAL_TOUCH = "mob_metal_javelin_touch";
    private static final String MOB_METAL_THROW_BEYOND_REACH = "mob_metal_javelin_beyond_reach";
    private static final String MOB_METAL_TOUCH_THEN_MELEE = "mob_metal_javelin_touch_then_melee";
    private static final String MOB_METAL_MELEE_THEN_TOUCH = "mob_metal_javelin_melee_then_touch";
    private static final String MOB_ATTACK_STAYS_VANILLA = "mob_attack_stays_vanilla";
    private static final String MOB_EXO_GAUNTLET_HIT = "mob_exo_gauntlet_hit";
    private static final String SELF_ENDER_BLINK = "self_ender_blink";
    private static final String SELF_GATED_BLINK_REFUSED = "self_gated_blink_refused";
    private static final String SELF_KINDLE_EATS_FIRST = "self_kindle_eats_before_the_embers";
    private static final String SELF_KINDLE_RELEASED_RUNS_NOTHING = "self_kindle_released_runs_nothing";
    private static final String SELF_TYPHOON_PROPEL = "self_typhoon_propel";
    private static final String SELF_KINDLE_SHIELDS = "self_kindle_shields_then_quenches";
    private static final String SELF_KINDLE_BURNS = "self_kindle_burns_the_attacker";
    private static final String SELF_HEART_BREWS_REPLACE = "self_heart_brews_replace_each_other";
    private static final String SELF_BARKSKIN_FIRE = "self_barkskin_fire_burns_through_arrow_breaks_bark";
    private static final String SELF_BARKSKIN_THORNS = "self_barkskin_thorns_and_the_axe";
    private static final String SELF_KINDLE_FIRE = "self_kindle_fire_relights_for_a_heart";
    private static final String BREW_EVERY_POTION_CARRIES = "brew_every_potion_carries_its_effect";
    private static final String BREW_BLAZE_KINDLES = "brew_blaze_kindles_for_an_hour";
    private static final String BREW_LEAF_BARKS = "brew_leaf_barks_for_an_hour";
    private static final String BREW_WITHOUT_ABILITY = "brew_without_an_ability_runs_nothing";
    private static final String STREAM_BLAZE_SPITFIRE = "stream_blaze_spitfire";
    private static final String MYCOSIS_SPREADS_ON_DEATH = "mycosis_spreads_on_death";
    private static final String MYCOSIS_PLACES_BUDS = "mycosis_places_buds";
    private static final String MYCOSIS_BUDS_AT_THE_FEET = "mycosis_buds_at_the_feet";
    private static final String MYCOSIS_TAP_POISONS_BELOW = "mycosis_tap_poisons_below";
    private static final String COLONIZE_SPREADS_NYLIUM = "colonize_spreads_nylium";
    private static final String COLONIZE_STARTS_MYCELIUM = "colonize_starts_mycelium";
    private static final String FUNGAL_SHIFT_TO_A_MUSHROOM = "fungal_shift_to_a_mushroom";
    private static final String FUNGAL_SHIFT_REFUSES_STONE = "fungal_shift_refuses_stone";
    private static final String SIGHT_EXTENDS_THE_SHIFT = "sight_extends_the_shift";
    private static final String BREW_SHROOM_SIGHTS = "brew_shroom_sights_for_an_hour";
    private static final String MOB_CRYSTAL = "mob_crystal_flechettes";
    private static final String MOB_LEAF = "mob_leaf_entangle";
    private static final String MOB_VITAL = "mob_vital_clone";
    private static final String MOB_SHROOM = "mob_shroom_debuff";
    private static final String MOB_ROCK = "mob_rock_petrify";
    private static final String MOB_BLAZE = "mob_blaze_ignite";
    private static final String MOB_FROST = "mob_frost_snap";
    private static final String MOB_TYPHOON = "mob_typhoon_levitate";
    private static final String MOB_GLOW = "mob_glow_laser";
    private static final String MOB_HEX = "mob_hex_charm";
    private static final String MOB_PULSE = "mob_pulse_stun";
    private static final String MOB_NETHER = "mob_nether_wither";
    private static final String MOB_ENDER = "mob_ender_teleport";
    private static final String MOB_UNSTABLE = "mob_unstable_explode";
    private static final String MOB_AEON = "mob_aeon_time_stop";
    private static final String MOB_AEON_RITUAL_COUNTS = "mob_aeon_ritual_counts";
    private static final String MOB_AEON_RITUAL_EGG = "mob_aeon_ritual_egg";
    private static final String MOB_AEON_RITUAL_BABY = "mob_aeon_ritual_baby";
    private static final String MOB_AEON_RITUAL_BABY_EGG = "mob_aeon_ritual_baby_egg";
    private static final String MOB_AEON_RITUAL_NO_BABY_FORM = "mob_aeon_ritual_no_baby_form";
    private static final String MOB_AEON_BABY_FORM_FILTER = "mob_aeon_baby_form_filter";

    // --- Lighting ---
    private static final String LIGHT_CANISTER_SYNC = "light_canister_sync";
    private static final String LIGHT_CANISTER_LOAD = "light_canister_load";
    private static final String LIGHT_DATAPACK_TYPE = "light_datapack_type";
    private static final String LIGHT_VAT_STACK = "light_vat_stack";
    private static final String LIGHT_VAT_DATA_PACKET = "light_vat_data_packet";

    private GooTestFunctions() {
    }

    /**
     * Registers all gametest functions into the TEST_FUNCTION registry.
     *
     * @param event the register event
     */
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registrar -> {
            reg(registrar, SMOKE, GameTestHelper::succeed);
            registerGooTypeRegistryTests(registrar);
            reg(registrar, VALUES_FRESH_AFTER_STOP, GooValueLifecycleTests::freshRegistryReadsAfterStop);
            registerGooFluidTests(registrar);
            registerGooItemTests(registrar);
            registerExoriteTests(registrar);
            registerGasketTests(registrar);
            registerGasketRegistryTests(registrar);
            registerEffectExecutorTests(registrar);
            registerAbilityLandingTests(registrar);
            registerCrucibleTests(registrar);
            registerPlacementTests(registrar);
            registerCanisterInteractionTests(registrar);
            registerGasketRemovalTests(registrar);
            registerMachineInteractionTests(registrar);
            registerMachineTests(registrar);
            registerMobEffectTests(registrar);
            registerLightingTests(registrar);
            registerTapDripTests(registrar);
            registerLabTests(registrar);
            registerBrewingTests(registrar);
            registerCrystallizerTests(registrar);
            registerGasketDemandTests(registrar);
        });
    }

    private static void registerGasketDemandTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, GASKET_DEMAND_VAT_FILLS_HUB, GasketDemandTests::vatFillsHubAtItsRestingDemand);
        reg(r, GASKET_DEMAND_VAT_FEEDS_TAP, GasketDemandTests::vatFeedsTapAtTheValveRate);
        reg(r, GASKET_DEMAND_TAP_CANISTER_FIRST, GasketDemandTests::tapDrainsItsCanisterBeforeAskingTheVat);
        reg(r, GASKET_DEMAND_TAP_TYPES_BY_TURNS, GasketDemandTests::vatFeedsTapBothTypesByTurns);
        reg(r, GASKET_DEMAND_CRYSTALLIZER_CHAIN, GasketDemandTests::crystallizerDrawsItsPaceThroughACanister);
        reg(r, GASKET_DEMAND_CRYSTALLIZER_VAT_CHAIN, GasketDemandTests::crystallizerDrawsItsPaceThroughAVat);
        reg(r, GASKET_DEMAND_HUB_SHARES, GasketDemandTests::hubGivesEachCanisterItsOwnDemand);
    }

    private static void registerCrystallizerTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, CRYSTALLIZER_CRYSTAL_FIRST, CrystallizerTests::crystalFirstThenEnder);
        reg(r, CRYSTALLIZER_EITHER_SLOT, CrystallizerTests::eitherSlotHoldsTheCrystal);
        reg(r, CRYSTALLIZER_TWO_CRYSTAL, CrystallizerTests::twoCrystalCanistersGrowCrystal);
        reg(r, CRYSTALLIZER_TAKES_ONLY_TWO_SLOTS, CrystallizerTests::canisterBlockAboveTakesOnlyTheTwoSlots);
        reg(r, CRYSTALLIZER_CANISTERS_AT_ITS_CENTERS, CrystallizerTests::canisterBlockOnCrystallizerStandsAtItsCenters);
        reg(r, CRYSTALLIZER_ADVANCES_TO_BUDDING_CHRYSM, CrystallizerTests::advancesToBuddingChrysm);
        reg(r, CRYSTALLIZER_SMALL_DIAL_HOLDS, CrystallizerTests::smallDialHoldsAtChrysm);
        reg(r, CRYSTALLIZER_DIAL_WRAPS, CrystallizerTests::dialClickWrapsFromLargeToSmall);
        reg(r, CRYSTALLIZER_PAUSES_WITHOUT_CRYSTAL, CrystallizerTests::pausesWithoutCrystal);
        reg(r, CRYSTALLIZER_PART_GROWN_NOT_CLICKABLE, CrystallizerTests::partGrownCrystalIsNotClickable);
        reg(r, CRYSTALLIZER_DIAL_KEEPS_GROWING, CrystallizerTests::dialStepKeepsAGrowingCrystal);
        reg(r, CRYSTALLIZER_OFF_PAUSES, CrystallizerTests::offPausesAGrowingCrystal);
        reg(r, CRYSTALLIZER_HANDS_THE_EXCESS, CrystallizerTests::clickHandsTheDialsTierAndTheExcess);
        reg(r, CRYSTALLIZER_ANY_ITEM_TAKES, CrystallizerTests::anyHeldItemTakesTheCrystal);
        reg(r, CRYSTALLIZER_PASSES_OTHER_CLICKS, CrystallizerTests::clicksItDoesNotOwnPass);
        reg(r, CRYSTALLIZER_CANISTER_SOUNDS, CrystallizerTests::eachCanisterPlacedPlaysASound);
        reg(r, CRYSTALLIZER_POUR_AIMED_ONLY, CrystallizerTests::aPourFillsOnlyTheAimedCanister);
        reg(r, CRYSTALLIZER_ONE_GROWING_GOO, CrystallizerTests::oneCanisterHoldsTheGrowingGoo);
        reg(r, CRYSTALLIZER_GASKET_FILLS_CANISTER, CrystallizerTests::gasketFillsTheIngredientCanister);
        reg(r, CRYSTALLIZER_CLICK_CRYSTAL, CrystallizerTests::clickingTheCrystalTakesTheChrysm);
        reg(r, CRYSTALLIZER_EVEN_PACE, CrystallizerTests::crystallizesAtAnEvenPace);
        for (ChrysmTier tier : ChrysmTier.values()) {
            reg(r, CRYSTALLIZER_STANDING_AIM_TAKES + tier.registryPath(),
                    h -> CrystallizerTests.standingAimTakesTheCrystal(h, tier));
        }
        reg(r, CRYSTALLIZER_OFF_CRYSTALLIZES_NOTHING, CrystallizerTests::offCrystallizesNothing);
    }

    private static void registerBrewingTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, BREWING_GOO_NEVER_BREWS, BrewingTests::gooNeverBrews);
        reg(r, BREWING_CHRYSM_BREWS_POTION, BrewingTests::chrysmBrewsTypePotion);
        reg(r, BREWING_CHRYSM_BREWS_SPLASH, BrewingTests::chrysmBrewsTypeSplashPotion);
        reg(r, BREWING_CHRYSM_BREWS_LINGERING, BrewingTests::chrysmBrewsTypeLingeringPotion);
        reg(r, BREWING_HIGHER_TIERS_NEVER_BREW, BrewingTests::higherTiersNeverBrew);
    }

    private static void registerTapDripTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, TAP_DRIP_DRAWS_ONE_MB, TapDripTests::tapDripDrawsOneMb);
        reg(r, TAP_VALVE_GATES_DRIP, TapDripTests::tapValveGatesDrip);
        reg(r, TAP_DRIP_LANDS_BELOW, TapDripTests::tapDripLandsBelow);
        reg(r, TAP_DRIP_BOTTOMLESS, TapDripTests::tapDripBottomless);
        reg(r, TAP_HOST_PLACES_ABOVE_LANDING, TapDripTests::tapHostPlacesAboveLanding);
        reg(r, TAP_DRIP_NO_ABILITY, TapDripTests::tapDripNoAbility);
        reg(r, TAP_DRIP_SENDS_TAP_DRIP, TapDripTests::tapDripSendsTapDrip);
        reg(r, TAP_VALVE_STEPS_FIVE_GRADES, TapDripTests::tapValveStepsFiveGrades);
        reg(r, TAP_SNEAK_CLICK_STEPS_VALVE_BACK, TapDripTests::tapSneakClickStepsValveBack);
        reg(r, TAP_DRIP_FILLS_CRUCIBLE_BELOW, TapDripTests::tapDripFillsCrucibleBelow);
        reg(r, TAP_DRIP_ONE_TO_FOUR_FILLS_CRUCIBLE, TapDripTests::tapDripOneToFourFillsCrucible);
        reg(r, TAP_DRIP_INTO_CRUCIBLE_RUNS_NO_PROGRAM, TapDripTests::tapDripIntoCrucibleRunsNoProgram);
        reg(r, TAP_DRIP_ON_REFUSING_BLOCK_RUNS_PROGRAM, TapDripTests::tapDripOnRefusingBlockRunsProgram);
    }

    private static void registerLightingTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, LIGHT_CANISTER_SYNC, LightingTests::filledCanisterLightsNeighbour);
        reg(r, LIGHT_CANISTER_LOAD, LightingTests::loadedCanisterLightsNeighbour);
        reg(r, LIGHT_DATAPACK_TYPE, LightingTests::datapackLightLevelDrivesCanisterEmission);
        reg(r, LIGHT_VAT_STACK, LightingTests::vatStackLightsWithItsGoo);
        reg(r, LIGHT_VAT_DATA_PACKET, LightingTests::vatDataPacketLightsVat);
    }

    private static void registerGooTypeRegistryTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, TYPES_BUNDLED_RESOLVE, GooTypeRegistryTests::bundledTypesResolve);
        reg(r, TYPES_DATAPACK_LISTED, GooTypeRegistryTests::datapackTypeListed);
        reg(r, TYPES_MARKER_RELOADS, GooTypeRegistryTests::abilityBlockReloadsType);
        reg(r, GLOVE_TYPE_ONLY_REFUSED, GloveSelectTests::typeOnlySelectionRefused);
        reg(r, GLOVE_GATED_SELECTION_REFUSED, GloveSelectTests::gatedSelectionRefusedWithoutTheRecipe);
        reg(r, GLOVE_SHIFT_RECOLLECTS_MARKER, GloveRecollectTests::shiftClickRecollectsMarker);
        reg(r, GLOVE_CLICK_NO_USING_STATE, GloveUseTests::rightClickEntersNoUsingState);
        reg(r, GLOVE_FIRST_SOURCE_DEPLETES_FIRST, FirstSourceTests::firstSourceIsTheStackDepleteShrinks);
        reg(r, TYPES_GLOVE_RELOADS, GooTypeRegistryTests::gloveSelectionReloadsType);
    }

    private static void registerGooFluidTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, FLUID_TYPES_SIDE_BY_SIDE, GooFluidTests::placedTypesStaySideBySide);
        reg(r, FLUID_FIELDS_STAMPED, GooFluidTests::fluidFieldsReadStampedType);
    }

    private static void registerGooItemTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, ITEM_THROWN_GOO_OWN_TYPE, GooItemTests::thrownGooLandOwnType);
        reg(r, ITEM_TAB_DATAPACK_TYPE, GooItemTests::creativeTabOffersDatapackType);
    }

    private static void registerExoriteTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, EXORITE_TEMPLATE_REGISTERED, ExoriteTests::templateRegistered);
        reg(r, EXORITE_TEMPLATE_IN_ANCIENT_CITY_LOOT, ExoriteTests::templateInAncientCityLoot);
        reg(r, EXORITE_TEMPLATE_DUPLICATES, ExoriteTests::templateDuplicates);
        reg(r, EXORITE_SET_REGISTERED, ExoriteTests::setRegistered);
        reg(r, EXORITE_SET_SMITHING, ExoriteTests::setSmithing);
        reg(r, EXORITE_SET_REPAIRS, ExoriteTests::setRepairs);
        reg(r, EXORITE_ARMOR_STATS, ExoriteTests::armorOutranksNetherite);
        reg(r, EXORITE_SURVIVES_ZERO_DURABILITY, ExoriteDurabilityTests::survivesZeroDurability);
        reg(r, EXORITE_BROKEN_ACTS_AS_HAND, ExoriteDurabilityTests::brokenActsAsHand);
        reg(r, EXORITE_BROKEN_TOOLTIP, ExoriteDurabilityTests::brokenTooltip);
        reg(r, EXORITE_ANVIL_REPAIR, ExoriteDurabilityTests::anvilRepair);
        reg(r, SOUL_BOUND_TAG_HOLDS_EXORITE, SoulBoundTests::tagHoldsExorite);
        reg(r, SOUL_BOUND_SURVIVES_DEATH, SoulBoundTests::survivesDeath);
        reg(r, KNOWN_ITEMS_SURVIVE_DEATH, SoulBoundTests::knownItemsSurviveDeath);
        reg(r, EXO_GAUNTLET_SMITHING, ExoriteTests::exoGauntletSmithing);
        reg(r, EXO_GAUNTLET_KEEPS_BENEFITS, GooItemTests::exoGauntletKeepsBenefits);
        reg(r, EXORITE_NOT_ENCHANTABLE, ExoriteEnchantingTests::notEnchantable);
        reg(r, EXORITE_ANVIL_REFUSES_BOOK, ExoriteEnchantingTests::anvilRefusesBook);
        reg(r, EXORITE_BARS_REGISTERED, ExoriteBarsTests::registered);
        reg(r, EXORITE_BARS_CRAFTED, ExoriteBarsTests::crafted);
        reg(r, EXORITE_BARS_STRENGTH, ExoriteBarsTests::strength);
        reg(r, EXORITE_BARS_DROPS, ExoriteBarsTests::dropsItself);
        reg(r, SPAWNER_CRAFTED_FROM_EXORITE_BARS, SpawnerRecipeTests::craftedFromExoriteBars);
        reg(r, EMPTY_SPAWNER_TAKES_EGG, SpawnerRecipeTests::emptySpawnerTakesEgg);
    }

    private static void registerGasketTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, PUSHER_EMPTY_RESERVOIR, GasketPusherTests::emptyReservoirSkipsTick);
        reg(r, PUSHER_NO_PARTNER, GasketPusherTests::noPartnerSkipsTick);
        reg(r, PUSHER_DISPOSE_DROPS_GASKET, GasketPusherTests::disposeDropsGasketOnce);
        reg(r, PUSHER_DOUBLE_DISPOSE_DROPS_GASKETS, GasketPusherTests::doubleDisposeDropsGasketEachTime);
        reg(r, PUSHER_REACTOR_OUTPUT_PUSH, GasketPusherTests::reactorOutputPushesToLinkedReceiver);
        reg(r, PUSHER_REACTOR_OUTPUT_REMOVAL, GasketPusherTests::reactorOutputRemovalStopsPush);
        reg(r, PUSHER_WATERLOGGED_GASKET_VAT, GasketPusherTests::waterloggedGasketPushesIntoVat);
        reg(r, PUSHER_BREAK_RELEASES_CHUNK, GasketPusherTests::crucibleBreakReleasesPartnerChunk);
        reg(r, PUSHER_UNLOAD_RELEASES_CHUNK, GasketPusherTests::crucibleUnloadReleasesPartnerChunk);
        reg(r, PUSHER_MOVED_RECEIVER, GasketPusherTests::movedReceiverKeepsReceiving);
        reg(r, CRUCIBLE_ROLE_TRANSMITTER, GasketHolderTests::crucibleResolveRoleAlwaysTransmitter);
        reg(r, CRUCIBLE_NO_GASKET, GasketHolderTests::crucibleNoGasketUnsupported);
        reg(r, CRUCIBLE_WITH_GASKET, GasketHolderTests::crucibleWithGasketSupported);
        reg(r, VAT_SUPPORTS_ROLE, GasketHolderTests::vatSupportsRoleMatchesBlockstate);
        reg(r, HUB_HAS_INTAKE, GasketHolderTests::hubHasIntake);
        reg(r, DEFAULT_ALLOWS_TUNING, GasketHolderTests::defaultAllowsTuningIsTrue);
        reg(r, REACTOR_GASKET_INSTALL, GasketHolderTests::reactorGasketInstallsOnOutputCanister);
        reg(r, REACTOR_TUNER_LINK, GasketHolderTests::reactorTunerLinksCrucibleToOutputCanister);
        reg(r, CRUCIBLE_GOO_GASKET_TUNER, GasketHolderTests::crucibleHoldingGooTakesGasketAndTuner);
        reg(r, REACTOR_GASKET_LOCATION, GasketHolderTests::reactorOutputGasketLocationFollowsCanister);
        reg(r, REACTOR_SEATED_GASKET_METADATA, GasketHolderTests::reactorSeatedCanisterAnswersGasketMetadata);
        reg(r, TAP_ROLE_RECEIVER, GasketHolderTests::tapResolveRoleAlwaysReceiver);
        reg(r, TAP_TUNER_LINK, GasketHolderTests::tapTunerLinksCanisterTransmitter);
        reg(r, TAP_REFUSES_TRANSMITTER, GasketHolderTests::tapRefusesTransmitterRole);
        reg(r, HUB_SLOT_TRANSMITS, GasketHolderTests::hubSlotLinksAsTransmitter);
        reg(r, HUB_SUPPORTS_BOTH_ROLES, GasketHolderTests::hubSupportsBothRolesIntakeReceives);
        reg(r, HUB_SLOT_MISS_INTAKE, GasketHolderTests::hubSlotMissKeepsIntakePath);
    }

    private static void registerGasketRegistryTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, TAP_ATTACHMENT_LOADS, GasketRegistryTests::tapAttachmentLoads);
        reg(r, HUB_SLOT_GASKET_REGISTERED, GasketRegistryTests::hubSlotGasketRegistered);
        reg(r, BREAK_POPS_GASKET_CRUCIBLE, GasketRegistryTests::breakPopsGasketCrucible);
        reg(r, BREAK_POPS_GASKET_VAT, GasketRegistryTests::breakPopsGasketVat);
        reg(r, BREAK_POPS_GASKET_TAP, GasketRegistryTests::breakPopsGasketTap);
        reg(r, BREAK_POPS_GASKET_HUB, GasketRegistryTests::breakPopsGasketHub);
        reg(r, REMOVAL_POPS_GASKET_EVERY_MACHINE, GasketRegistryTests::removalPopsGasketEveryMachine);
        reg(r, BREAK_RELEASES_STANDING_GASKET, GasketRegistryTests::breakReleasesStandingGasket);
        reg(r, PLACED_HUB_REGISTERS_CANISTER, GasketRegistryTests::placedHubRegistersCarriedCanister);
    }

    private static void registerEffectExecutorTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, FX_METAL, EffectExecutorTests::metalRuns);
        reg(r, FX_CRYSTAL, EffectExecutorTests::crystalRuns);
        reg(r, FX_NETHER, EffectExecutorTests::netherImplodes);
        reg(r, FX_UNSTABLE, EffectExecutorTests::unstableExplodes);
        reg(r, FX_PROGRAM_GLOW_WALL, EffectExecutorTests::programGlowWall);
        reg(r, FX_PROGRAM_GLOW_FLOOR, EffectExecutorTests::programGlowFloor);
        reg(r, FX_PROGRAM_INSTANT, EffectExecutorTests::programInstantDetonation);
        reg(r, FX_PROGRAM_TIMED, EffectExecutorTests::programTimedBomb);
        reg(r, FX_PROGRAM_MINE, EffectExecutorTests::programProximityMine);
        reg(r, FX_PROGRAM_METAL_SPIKES, EffectExecutorTests::programMetalSpikes);
        reg(r, FX_PROGRAM_CRYSTAL_CLOUD, EffectExecutorTests::programCrystalCloud);
        reg(r, FX_PROGRAM_NETHER_BLACK_HOLE, EffectExecutorTests::programNetherBlackHole);
        reg(r, FX_LANDED_CRYSTAL_CLOUD, EffectExecutorTests::crystalCloudLiveAfterLanding);
        reg(r, FX_LANDED_METAL_SPIKES, EffectExecutorTests::metalSpikesLiveAfterLanding);
        reg(r, FX_LANDED_BLACK_HOLE, EffectExecutorTests::blackHoleGathersAfterLanding);
        reg(r, FX_LANDED_BLAST, EffectExecutorTests::blastExplodesAfterLanding);
        reg(r, FX_LANDED_MINE, EffectExecutorTests::mineAwaitsAfterLanding);
        reg(r, FX_LANDED_GLOW_CRYSTAL, EffectExecutorTests::glowCrystalStandsAfterLanding);
    }

    private static void registerAbilityLandingTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, FX_FALLEN_MARKER_KEEPS_ABILITY, EffectExecutorTests::fallenMarkerKeepsAbility);
        reg(r, FX_CRYSTAL_GROWS, EffectExecutorTests::crystalNeverGrowsOnALaterHit);
        reg(r, FX_OTHER_ABILITY_MARKS_CRYSTAL, EffectExecutorTests::otherAbilityMarksCrystal);
        reg(r, FX_NO_ABILITY_LANDS_NOTHING, BlockLandingTests::noAbilityLandsNothing);
        reg(r, PRISM_GROWS_ON_THE_FACE, PrismTests::prismGrowsOnTheFace);
        reg(r, PRISM_REFUSED_WITHOUT_QUARTZ, PrismTests::prismRefusedWithoutQuartz);
        reg(r, PRISM_COMBO_RUNS_THE_TYPE_PRISM_ABILITY, PrismComboTests::comboRunsTheTypePrismAbility);
        reg(r, PRISM_COMBO_RUNS_ON_PRISM_BEHAVIORS, PrismComboTests::comboRunsOnPrismBehaviors);
        reg(r, PRISM_WITHOUT_COMBO_STAYS, PrismComboTests::prismWithoutComboStays);
        reg(r, COMBINED_PRISM_REFUSES_SECOND, PrismComboTests::combinedPrismRefusesSecond);
        reg(r, FX_ABILITY_LANDS_MARKER, BlockLandingTests::abilityLandsItsMarker);
        reg(r, FX_BLAST_LANDS_NO_BLOCK, BlockLandingTests::blastLandsNoBlock);
        reg(r, FREE_BLAST_AT_AIR_POINT, FreeAimTests::blastExplodesAtThePointInOpenAir);
        reg(r, FREE_BLAST_AT_SKY, FreeAimTests::blastAimedAtTheSkyThrowsToTheRangesEnd);
        reg(r, BLAST_CRATER_INSIDE_SPHERE, GooExplosionTests::blastCraterStaysInsideItsSphere);
        reg(r, BLAST_DROPS_EVERY_DIRT, GooExplosionTests::blastDropsEveryDirtItBreaks);
        reg(r, BLAST_SPARES_ITEMS, GooExplosionTests::blastSparesItemsInItsSphere);
        reg(r, FX_CLOUD_BLOCK_GOES, EffectExecutorTests::crystalCloudBlockGoesWithItsProgram);
        reg(r, FX_TRAP_BLOCK_GOES, EffectExecutorTests::metalTrapBlockGoesWithItsProgram);
    }

    private static void registerMachineInteractionTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, IX_TAP_VALVE, MachineInteractionTests::tapCanisterInsert);
        reg(r, IX_TAP_TOP_CLICK_INSERT, MachineInteractionTests::tapTopClickInsertsCanister);
        reg(r, IX_TAP_SLOT_CLICK_INSERT, MachineInteractionTests::tapSlotRegionClickInsertsCanister);
        reg(r, IX_TAP_EMPTY_HAND_TAKE, MachineInteractionTests::tapEmptyHandClickTakesCanister);
        reg(r, IX_TAP_GOO_POUR, MachineInteractionTests::tapGooClickPoursIntoSlottedCanister);
        reg(r, IX_VAT_GASKET, MachineInteractionTests::vatGasketApply);
        reg(r, IX_HUB_INSERT, MachineInteractionTests::hubCanisterInsert);
        reg(r, IX_HUB_PICKUP, MachineInteractionTests::hubCanisterPickup);
        reg(r, IX_HUB_POUR_AIMED_ONLY, MachineInteractionTests::hubPourFillsOnlyTheAimedCanister);
        reg(r, IX_PLEXER_TARGET, MachineInteractionTests::plexerSetTarget);
        reg(r, IX_PLEXER_REFUSES_UNLEARNED, MachineInteractionTests::plexerRefusesAnUnlearnedTarget);
        reg(r, IX_REACTOR_INSERT_PICKUP, MachineInteractionTests::reactorCanisterInsertThenSneakPickup);
        reg(r, IX_CRUCIBLE_BLAZE_ROD_COLD, MachineInteractionTests::crucibleBlazeRodClickLeavesItCold);
        reg(r, IX_CRUCIBLE_COLD_ABSORBS_NOTHING, MachineInteractionTests::coldCrucibleAbsorbsNothing);
        reg(r, IX_CRUCIBLE_BLAZE_ABSORBS, MachineInteractionTests::blazeCrucibleAbsorbsItem);
        reg(r, IX_CRUCIBLE_FLINT_SPARKS, MachineInteractionTests::flintAndSteelSparksColdCrucible);
        reg(r, IX_CRUCIBLE_SPARK_MELTS_COAL, MachineInteractionTests::sparkedCrucibleMeltsCoalOnItsBlaze);
        reg(r, IX_HUB_ITEM_GOO_INSERT, HubItemClickTests::gooInsertFillsCanisterAndPlaces);
        reg(r, IX_HUB_ITEM_GOO_INSERT_REMAINDER, HubItemClickTests::gooInsertKeepsRemainder);
        reg(r, IX_HUB_ITEM_INSERT_REFUSED, HubItemClickTests::insertRefusedLeavesStacks);
        reg(r, IX_HUB_ITEM_DRAINS_NOTHING, HubItemClickTests::secondaryClickDrainsNothing);
        reg(r, IX_HUB_ITEM_IS_GOO_SOURCE, GooSourceScannerTests::hubItemIsAGooSource);
        reg(r, IX_VAT_ITEM_GOO_INSERT, VatItemClickTests::gooInsertFillsVatAndFullRefuses);
        reg(r, IX_VAT_ITEM_GOO_INSERT_REMAINDER, VatItemClickTests::gooInsertKeepsRemainder);
        reg(r, IX_VAT_ITEM_DRAIN, VatItemClickTests::secondaryClickUnpacksEveryType);
        reg(r, IX_GOO_INSERT_SHARED, GooInsertTests::pourDepletesByAccepted);
        reg(r, IX_VAT_STREAM_HOLDS, VatStreamTests::gooClickHoldsStream);
        reg(r, IX_CRUCIBLE_TOPS_UP_CANISTER, DrainIntoInventoryTests::crucibleTopsUpCarriedCanister);
        reg(r, IX_VAT_UNPACKS_EVERY_TYPE, DrainIntoInventoryTests::vatUnpacksEveryTypeIntoInventory);
    }

    private static void registerCrucibleTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, CR_GOO_INSERT, CrucibleTests::gooInsertViaInteraction);
        reg(r, CR_ITEM_ABSORB, CrucibleTests::itemEntityAbsorption);
        reg(r, CR_THROWN_ITEM_TEACHES, CrucibleTests::thrownItemTeachesTheThrower);
        reg(r, CR_THROWN_CONTAINER_TEACHES, CrucibleTests::thrownContainerTeachesItsContents);
        reg(r, CR_UNTHROWN_ITEM_TEACHES_NOBODY, CrucibleTests::unthrownItemTeachesNobody);
        reg(r, CR_MELTS_CHRYSM, CrucibleTests::meltsChrysm);
        reg(r, CR_CAP_EACH_TYPE, CrucibleTests::reservoirCapsEachType);
        reg(r, CR_CAP_GOO_IN_HAND, CrucibleTests::gooInHandRefusedAtCap);
        reg(r, CR_CAP_GOO_ENTITY, CrucibleTests::gooEntityRefusedAtCap);
        reg(r, CR_CAP_ITEMS_THAT_FIT, CrucibleTests::itemStackMeltsWholeItemsThatFit);
        reg(r, CR_CAP_CONTAINER, CrucibleTests::containerRefusedWholeAtCap);
        reg(r, CR_CAP_MELTED_ITEM, CrucibleTests::meltedItemRefusedWholeAtCap);
        reg(r, CR_CAP_ACCOUNTED, CrucibleTests::fillPastTheCapAccountsForEveryMb);
        reg(r, CR_GOO_STACK_WHOLE, CrucibleTests::gooStackConsumedWhole);
        reg(r, CR_GOO_STACK_TO_CAP, CrucibleTests::gooStackFillsToTheCap);
        reg(r, CR_FIRST_MELT_PUDDLE, CrucibleTests::firstMeltTicksDrawAPuddle);
        reg(r, CR_DROP_RESTS_ON_FLOOR, CrucibleTests::droppedItemRestsOnBasinFloor);
        reg(r, CR_LEDGE_ITEM_TAKEN_INSIDE, CrucibleTests::ledgeItemTakenOnlyInsideTheCavity);
        reg(r, CR_WALL_TOP_ITEMS_SLIDE_IN, CrucibleTests::wallTopItemsSlideIntoTheCavity);
        reg(r, CR_LEDGE_ITEM_LIFTED_IN, CrucibleTests::ledgeItemLiftedIntoTheCavity);
        reg(r, CR_THROWN_ITEM_CAUGHT, CrucibleTests::thrownItemCaughtByTheField);
        reg(r, CR_SIDE_HIT_LIFTED_IN, CrucibleTests::sideHitItemLiftedIntoTheCavity);
        reg(r, CR_ROUGH_THROW_STEERED_IN, CrucibleTests::roughThrowSteeredIntoTheMouth);
        reg(r, CR_ITEM_TAKEN_AT_KILL_BOX, CrucibleTests::droppedItemTakenAtTheKillBox);
        reg(r, CR_COLD_ITEM_WAITS_ON_FLOOR, CrucibleTests::coldCrucibleItemWaitsOnTheFloor);
        reg(r, CR_ITEM_MELTS_ON_ITS_CLOCK, CrucibleTests::itemMeltsOnItsClock);
        reg(r, CR_BROKEN_MID_MELT_DROPS_REMAINDER, CrucibleTests::brokenMidMeltDropsTheRemainder);
        reg(r, CR_STACK_MELTS_ITEM_BY_ITEM, CrucibleTests::stackMeltsItemByItemInTurn);
        reg(r, CR_COMBO_MELTS_EVERY_ITEM, CrucibleTests::comboMeltsEveryItemAtOnce);
        reg(r, CR_SPARK_REFUSED_BLAZE, CrucibleTests::sparkRefusedOnBlazeGoo);
        reg(r, CR_SPARK_REFUSED_HEAT, CrucibleTests::sparkRefusedOnHeatTicks);
        reg(r, CR_SPARK_LIGHTS_COLD, CrucibleTests::sparkLightsColdEmptyCrucible);
    }

    private static void registerPlacementTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, PL_DOUBLE_STACK, PlacementTests::secondThrowLandsBesideTheFirst);
        reg(r, PL_SIDEWAYS_NEIGHBOR, PlacementTests::sidewaysMarkerSurvivesNeighborChange);
        reg(r, PL_OTHER_TYPES, PlacementTests::otherTypesPlaceMarker);
        reg(r, PL_ABILITY_HIT_BLOCK, PlacementTests::abilityTakesReplaceableHitBlock);
        reg(r, PL_ABILITY_WATERLOG, PlacementTests::abilityWaterlogsInWater);
        reg(r, PL_ABILITY_LAVA, PlacementTests::abilityRefusesLava);
        reg(r, PL_ABILITY_SAME_STACK, PlacementTests::otherAbilityLandsBesideAStandingMarker);
        reg(r, PL_OTHER_ABILITY_THROW_LEAVES_MARKER, StackKeyTests::secondThrowLandsItsOwnMarker);
        reg(r, PL_SECOND_THROW_COSTS_THE_SAME, FlatCostTests::secondThrowCostsTheSameAsTheFirst);
    }

    private static void registerGasketRemovalTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, SNEAK_POPS_CANISTER_SLOT_GASKET, GasketRemovalTests::canisterSlotGasketPopsCanisterStays);
        reg(r, SNEAK_POPS_HUB_GASKET, GasketRemovalTests::hubPopsHitGasketThenHandsBackCanister);
        reg(r, SNEAK_POPS_TAP_GASKET, GasketRemovalTests::tapPopsGasketThenHandsBackCanister);
        reg(r, SNEAK_POPS_VAT_GASKET, GasketRemovalTests::vatPopsHitFaceThenLeavesStateUnchanged);
        reg(r, SNEAK_POPS_REACTOR_GASKET, GasketRemovalTests::reactorPopsHitFaceThenLeavesStateUnchanged);
        reg(r, SNEAK_POPS_CRUCIBLE_GASKET, GasketRemovalTests::cruciblePopsGasketThenPasses);
    }

    private static void registerCanisterInteractionTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, IX_CANISTER_PLAIN_INSERT, CanisterInteractionTests::plainClickInserts);
        reg(r, IX_CANISTER_BUCKET_FILL, CanisterInteractionTests::bucketFillsAimedCanister);
        reg(r, IX_CANISTER_GASKET_INSTALL, CanisterInteractionTests::gasketClickInstallsOnAimedCanister);
        reg(r, IX_CANISTER_CLICK_PICKUP, CanisterInteractionTests::clickPicksUp);
        reg(r, IX_CANISTER_LAST_PICKUP, CanisterInteractionTests::lastPickupRemovesBlock);
        reg(r, IX_CANISTER_EMPTY_HAND, CanisterInteractionTests::emptyHandPicksUp);
        reg(r, IX_CANISTER_VAT_TOP_PLACES, CanisterPlacementTests::vatTopTakesCanister);
        reg(r, IX_CANISTER_STONE_TOP_PLACES, CanisterPlacementTests::stoneTopTakesCanister);
        reg(r, IX_CANISTER_CRUCIBLE_TOP_REFUSES, CanisterPlacementTests::crucibleTopRefusesCanister);
        reg(r, IX_CANISTER_SIGN_TOP_REFUSES, CanisterPlacementTests::signTopRefusesCanister);
        reg(r, IX_CANISTER_SNEAK_VAT_TOP_PLACES, CanisterPlacementTests::sneakClickVatTopTakesCanister);
        reg(r, IX_CANISTER_HUB_ATTACH_SPOT, CanisterPlacementTests::hubAttachSpotTakesCanisterAbove);
    }

    private static void registerMachineTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, MACHINE_CANISTER_INSERT, MachineTests::canisterInsertCreatesHandler);
        reg(r, MACHINE_CANISTER_REMOVE, MachineTests::canisterRemoveClearsHandler);
        reg(r, MACHINE_CANISTER_TICK, MachineTests::canisterTicksWithSlot);
        reg(r, MACHINE_CANISTER_BREAK_RELEASES_GASKET, MachineTests::canisterBreakReleasesSlotGasket);
        reg(r, MACHINE_CANISTER_FLUID, MachineTests::canisterFluidInsertExtract);
        reg(r, MACHINE_CANISTER_ROUTING, MachineTests::canisterFluidRouting);
        reg(r, MACHINE_CANISTER_ROUNDTRIP, MachineTests::canisterSurvivesRoundTrip);
        reg(r, MACHINE_REACTOR_WITHOUT_INPUTS, MachineTests::reactorWithoutInputsMakesNothing);
        reg(r, MACHINE_REACTOR_BREAK_RELEASES_GASKET, MachineTests::reactorBreakReleasesOutputGasket);
        reg(r, MACHINE_REACTOR_REACTION, MachineTests::reactorProcessesReaction);
        reg(r, MACHINE_REACTOR_REDSTONE, MachineTests::reactorRedstoneHalts);
        reg(r, MACHINE_PLEXER_WITHOUT_GOO, MachineTests::plexerWithoutGooMakesNothing);
    }

    private static void registerMobEffectTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, MOB_METAL, MobEffectTests::metalJavelin);
        reg(r, MOB_METAL_TOUCH, TouchDeliveryTests::javelinTouchesWithinReach);
        reg(r, MOB_METAL_THROW_BEYOND_REACH, TouchDeliveryTests::javelinThrowsBeyondReach);
        reg(r, MOB_METAL_TOUCH_THEN_MELEE, AttackTouchTests::touchThenMeleeLandInFull);
        reg(r, MOB_METAL_MELEE_THEN_TOUCH, AttackTouchTests::meleeThenTouchLandInFull);
        reg(r, MOB_ATTACK_STAYS_VANILLA, AttackTouchTests::attackStaysVanilla);
        reg(r, MOB_EXO_GAUNTLET_HIT, GloveDamageTests::exoGauntletHitsForSeven);
        reg(r, SELF_ENDER_BLINK, SelfDeliveryTests::enderBlink);
        reg(r, SELF_GATED_BLINK_REFUSED, SelfDeliveryTests::gatedBlinkRefusedWithoutTheRecipe);
        reg(r, SELF_KINDLE_EATS_FIRST, SelfDeliveryTests::kindleEatsBeforeTheEmbers);
        reg(r, SELF_KINDLE_RELEASED_RUNS_NOTHING, SelfDeliveryTests::kindleLetGoMidEatRunsNothing);
        reg(r, SELF_TYPHOON_PROPEL, SelfDeliveryTests::typhoonPropel);
        reg(r, SELF_KINDLE_SHIELDS, HeartOverlayTests::kindleShieldsThenQuenches);
        reg(r, SELF_KINDLE_BURNS, HeartOverlayTests::kindleBurnsTheAttacker);
        reg(r, SELF_HEART_BREWS_REPLACE, HeartOverlayTests::heartBrewsReplaceEachOther);
        reg(r, SELF_BARKSKIN_FIRE, BarkskinTests::fireBurnsThroughArrowBreaksBark);
        reg(r, SELF_BARKSKIN_THORNS, BarkskinTests::thornsAndTheAxe);
        reg(r, SELF_KINDLE_FIRE, HeartOverlayTests::kindleFireRelightsForAHeart);
        reg(r, BREW_EVERY_POTION_CARRIES, BrewEffectTests::everyPotionCarriesItsBrewEffect);
        reg(r, BREW_BLAZE_KINDLES, BrewEffectTests::blazeBrewKindlesForAnHour);
        reg(r, BREW_LEAF_BARKS, BrewEffectTests::leafBrewBarksForAnHour);
        reg(r, BREW_WITHOUT_ABILITY, BrewEffectTests::brewWithoutAnAbilityRunsNothing);
        reg(r, STREAM_BLAZE_SPITFIRE, StreamDeliveryTests::blazeSpitfire);
        reg(r, MYCOSIS_SPREADS_ON_DEATH, MycosisTests::mycosisSpreadsOnDeath);
        reg(r, MYCOSIS_PLACES_BUDS, MycosisTests::mycosisPlacesBuds);
        reg(r, MYCOSIS_BUDS_AT_THE_FEET, MycosisTests::mycosisBudsAtTheFeet);
        reg(r, MYCOSIS_TAP_POISONS_BELOW, MycosisTests::mycosisTapPoisonsBelow);
        reg(r, COLONIZE_SPREADS_NYLIUM, ColonizeTests::colonizeSpreadsNylium);
        reg(r, COLONIZE_STARTS_MYCELIUM, ColonizeTests::colonizeStartsMycelium);
        reg(r, FUNGAL_SHIFT_TO_A_MUSHROOM, FungalShiftTests::fungalShiftToAMushroom);
        reg(r, FUNGAL_SHIFT_REFUSES_STONE, FungalShiftTests::fungalShiftRefusesStone);
        reg(r, SIGHT_EXTENDS_THE_SHIFT, FungalShiftTests::sightExtendsTheShift);
        reg(r, BREW_SHROOM_SIGHTS, BrewEffectTests::shroomBrewSightForAnHour);
        reg(r, MOB_CRYSTAL, MobEffectTests::crystalFlechettes);
        reg(r, MOB_LEAF, MobEffectTests::leafEntangle);
        reg(r, MOB_VITAL, MobEffectTests::vitalClone);
        reg(r, MOB_SHROOM, MobEffectTests::shroomDebuff);
        reg(r, MOB_ROCK, MobEffectTests::rockPetrify);
        reg(r, MOB_BLAZE, MobEffectTests::blazeIgnite);
        reg(r, MOB_FROST, MobEffectTests::frostSnap);
        reg(r, MOB_TYPHOON, MobEffectTests::typhoonLevitate);
        reg(r, MOB_GLOW, MobEffectTests::glowLaser);
        reg(r, MOB_HEX, MobEffectTests::hexCharm);
        reg(r, MOB_PULSE, MobEffectTests::pulseStun);
        reg(r, MOB_NETHER, MobEffectTests::netherWither);
        reg(r, MOB_ENDER, MobEffectTests::enderTeleport);
        reg(r, MOB_UNSTABLE, MobEffectTests::unstableExplode);
        reg(r, MOB_AEON, MobEffectTests::aeonTimeStop);
        reg(r, MOB_AEON_RITUAL_COUNTS, MobEffectTests::aeonRitualCounts);
        reg(r, MOB_AEON_RITUAL_EGG, MobEffectTests::aeonRitualEgg);
        reg(r, MOB_AEON_RITUAL_BABY, MobEffectTests::aeonRitualBaby);
        reg(r, MOB_AEON_RITUAL_BABY_EGG, MobEffectTests::aeonRitualBabyEgg);
        reg(r, MOB_AEON_RITUAL_NO_BABY_FORM, MobEffectTests::aeonRitualNoBabyForm);
        reg(r, MOB_AEON_BABY_FORM_FILTER, MobEffectTests::aeonBabyFormFilter);
    }

    /**
     * Registers the Goo Lab build tests.
     *
     * @param r the registry registrar
     */
    private static void registerLabTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, LAB_BUILD_SHELL, LabTests::buildShell);
        reg(r, LAB_TEMPLATE_LOADS, LabTemplateTests::templateLoads);
        reg(r, LAB_BAY_TAP, LabBayTests::tapBay);
        reg(r, LAB_BAY_HUB, LabBayTests::hubBay);
        reg(r, LAB_BAY_GASKET, LabBayTests::gasketBay);
        reg(r, LAB_PENS_AND_RANGE, LabBayTests::pensAndRange);
        reg(r, LAB_SUPPLY_ROW, LabSupplyTests::supplyRow);
        reg(r, LAB_KIT, LabSupplyTests::kit);
        reg(r, LAB_REBUILD, LabRebuildTests::rebuild);
        reg(r, ZOMBIE_PEN_HOLDS_ONLY_ZOMBIES, LabTests::zombiePenHoldsOnlyZombies);
        reg(r, PASSIVE_PEN_HAS_NO_ROOF, LabTests::passivePenHasNoRoof);
        reg(r, LAB_KIT_TEACHES_EVERY_ABILITY, LabKitKnowledgeTests::kitTeachesEveryAbility);
        reg(r, LAB_KIT_UNTOUCHED_PLAYER_KNOWS_NOTHING, LabKitKnowledgeTests::untouchedPlayerKnowsNothing);
    }

    /**
     * Registers a single test function in the goo namespace.
     *
     * @param registrar the registry registrar
     * @param name      the function name
     * @param fn        the test function
     */
    private static void reg(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar,
                            String name, Consumer<GameTestHelper> fn) {
        registrar.register(Identifier.fromNamespaceAndPath(Goo.MODID, name), fn);
    }
}
