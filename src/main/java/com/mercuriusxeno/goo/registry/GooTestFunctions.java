package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.gametest.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Registers goo gametest functions via NeoForge's RegisterEvent.
 * TestFunctionLoader.runLoaders() fires during Bootstrap.bootStrap(),
 * before mod loading, so we use RegisterEvent instead to register
 * into the TEST_FUNCTION registry at the correct time.
 */
public final class GooTestFunctions {

    // --- Smoke ---
    private static final String SMOKE = "smoke";

    // --- Goo type registry ---
    private static final String TYPES_BUNDLED_RESOLVE = "types_bundled_resolve";
    private static final String TYPES_DATAPACK_LISTED = "types_datapack_listed";
    private static final String TYPES_MARKER_RELOADS = "types_marker_reloads";
    private static final String TYPES_GLOVE_RELOADS = "types_glove_reloads";

    // --- Generic goo fluid ---
    private static final String FLUID_TYPES_SIDE_BY_SIDE = "fluid_types_side_by_side";
    private static final String FLUID_FIELDS_STAMPED = "fluid_fields_stamped";

    // --- Generic goo items ---
    private static final String ITEM_THROWN_BLOBS_OWN_TYPE = "item_thrown_blobs_own_type";
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

    // --- GasketPusher ---
    private static final String PUSHER_EMPTY_RESERVOIR = "pusher_empty_reservoir";
    private static final String PUSHER_NO_PARTNER = "pusher_no_partner";
    private static final String PUSHER_DISPOSE_AND_TICK = "pusher_dispose_and_tick";
    private static final String PUSHER_DOUBLE_DISPOSE = "pusher_double_dispose";
    private static final String PUSHER_REACTOR_OUTPUT_PUSH = "pusher_reactor_output_push";
    private static final String PUSHER_REACTOR_OUTPUT_REMOVAL = "pusher_reactor_output_removal";

    // --- IGasketHolder ---
    private static final String CRUCIBLE_ROLE_TRANSMITTER = "crucible_role_transmitter";
    private static final String CRUCIBLE_NO_GASKET = "crucible_no_gasket";
    private static final String CRUCIBLE_WITH_GASKET = "crucible_with_gasket";
    private static final String VAT_SUPPORTS_ROLE = "vat_supports_role";
    private static final String HUB_HAS_INTAKE = "hub_has_intake";
    private static final String DEFAULT_ALLOWS_TUNING = "default_allows_tuning";
    private static final String REACTOR_GASKET_INSTALL = "reactor_gasket_install";
    private static final String REACTOR_TUNER_LINK = "reactor_tuner_link";
    private static final String REACTOR_GASKET_LOCATION = "reactor_gasket_location";
    private static final String REACTOR_SEATED_GASKET_METADATA = "reactor_seated_gasket_metadata";

    // --- Effect executors ---
    private static final String FX_BLAZE = "fx_blaze_mines";
    private static final String FX_ROCK = "fx_rock_mines";
    private static final String FX_FROST = "fx_frost_runs";
    private static final String FX_METAL = "fx_metal_runs";
    private static final String FX_CRYSTAL = "fx_crystal_runs";
    private static final String FX_NETHER = "fx_nether_implodes";
    private static final String FX_UNSTABLE = "fx_unstable_explodes";
    private static final String FX_GLOW_WALL = "fx_glow_wall";
    private static final String FX_GLOW_FLOOR = "fx_glow_floor";
    private static final String FX_PROGRAM_GLOW_WALL = "fx_program_glow_wall";
    private static final String FX_PROGRAM_GLOW_FLOOR = "fx_program_glow_floor";
    private static final String FX_ABILITY_BLAZE = "fx_ability_blaze_tunnel";
    private static final String FX_ABILITY_ROCK = "fx_ability_rock_tunnel";
    private static final String FX_ABILITY_FROST = "fx_ability_frost_sphere";
    private static final String FX_PROGRAM_INSTANT = "fx_program_instant_detonation";
    private static final String FX_PROGRAM_TIMED = "fx_program_timed_bomb";
    private static final String FX_PROGRAM_MINE = "fx_program_proximity_mine";
    private static final String FX_PROGRAM_METAL_SPIKES = "fx_program_metal_spikes";
    private static final String FX_PROGRAM_CRYSTAL_CLOUD = "fx_program_crystal_cloud";
    private static final String FX_PROGRAM_NETHER_BLACK_HOLE = "fx_program_nether_black_hole";

    // --- Crucible ---
    private static final String CR_BLOB_INSERT = "cr_blob_insert";
    private static final String CR_ITEM_ABSORB = "cr_item_absorb";

    // --- Placement ---
    private static final String PL_BLAZE = "pl_blaze_places";
    private static final String PL_ROCK = "pl_rock_places";
    private static final String PL_FROST = "pl_frost_places";
    private static final String PL_DOUBLE_STACK = "pl_double_hit_stacks";
    private static final String PL_SIDEWAYS_NEIGHBOR = "pl_sideways_neighbor";
    private static final String PL_OTHER_TYPES = "pl_other_types_place";

    // --- Canister interactions ---
    private static final String IX_CANISTER_SHIFT_INSERT = "ix_canister_shift_insert";
    private static final String IX_CANISTER_CLICK_PICKUP = "ix_canister_click_pickup";
    private static final String IX_CANISTER_LAST_PICKUP = "ix_canister_last_pickup";
    private static final String IX_CANISTER_EMPTY_HAND = "ix_canister_empty_hand";

    // --- Machine interactions ---
    private static final String IX_TAP_VALVE = "ix_tap_valve_toggle";
    private static final String IX_VAT_GASKET = "ix_vat_gasket_apply";
    private static final String IX_HUB_INSERT = "ix_hub_canister_insert";
    private static final String IX_HUB_PICKUP = "ix_hub_canister_pickup";
    private static final String IX_PLEXER_TARGET = "ix_plexer_set_target";
    private static final String IX_CRUCIBLE_FUEL = "ix_crucible_fuel_insert";

    // --- Machines ---
    private static final String MACHINE_CANISTER_INSERT = "machine_canister_insert";
    private static final String MACHINE_CANISTER_REMOVE = "machine_canister_remove";
    private static final String MACHINE_CANISTER_TICK = "machine_canister_tick";
    private static final String MACHINE_CANISTER_BREAK = "machine_canister_break";
    private static final String MACHINE_CANISTER_FLUID = "machine_canister_fluid";
    private static final String MACHINE_CANISTER_ROUTING = "machine_canister_routing";
    private static final String MACHINE_CANISTER_ROUNDTRIP = "machine_canister_roundtrip";
    private static final String MACHINE_REACTOR_IDLE = "machine_reactor_idle";
    private static final String MACHINE_REACTOR_BREAK = "machine_reactor_break";
    private static final String MACHINE_REACTOR_REACTION = "machine_reactor_reaction";
    private static final String MACHINE_REACTOR_REDSTONE = "machine_reactor_redstone";
    private static final String MACHINE_PLEXER_IDLE = "machine_plexer_idle";

    // --- MobEffects ---
    private static final String MOB_METAL = "mob_metal_javelin";
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

    // --- Lighting ---
    private static final String LIGHT_CANISTER_SYNC = "light_canister_sync";
    private static final String LIGHT_CANISTER_LOAD = "light_canister_load";
    private static final String LIGHT_DATAPACK_TYPE = "light_datapack_type";

    private GooTestFunctions() {
    }

    /**
     * Subscribes the registration listener to the mod event bus.
     * Call once during mod construction.
     *
     * @param modEventBus the mod event bus
     */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(GooTestFunctions::onRegister);
    }

    /**
     * Registers all gametest functions into the TEST_FUNCTION registry.
     *
     * @param event the register event
     */
    private static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registrar -> {
            reg(registrar, SMOKE, GameTestHelper::succeed);
            registerGooTypeRegistryTests(registrar);
            registerGooFluidTests(registrar);
            registerGooItemTests(registrar);
            registerExoriteTests(registrar);
            registerGasketTests(registrar);
            registerEffectExecutorTests(registrar);
            registerCrucibleTests(registrar);
            registerPlacementTests(registrar);
            registerCanisterInteractionTests(registrar);
            registerMachineInteractionTests(registrar);
            registerMachineTests(registrar);
            registerMobEffectTests(registrar);
            registerLightingTests(registrar);
        });
    }

    private static void registerLightingTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, LIGHT_CANISTER_SYNC, LightingTests::filledCanisterLightsNeighbour);
        reg(r, LIGHT_CANISTER_LOAD, LightingTests::loadedCanisterLightsNeighbour);
        reg(r, LIGHT_DATAPACK_TYPE, LightingTests::datapackLightLevelDrivesCanisterEmission);
    }

    private static void registerGooTypeRegistryTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, TYPES_BUNDLED_RESOLVE, GooTypeRegistryTests::bundledTypesResolve);
        reg(r, TYPES_DATAPACK_LISTED, GooTypeRegistryTests::datapackTypeListed);
        reg(r, TYPES_MARKER_RELOADS, GooTypeRegistryTests::chainMarkerReloadsType);
        reg(r, TYPES_GLOVE_RELOADS, GooTypeRegistryTests::gloveSelectionReloadsType);
    }

    private static void registerGooFluidTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, FLUID_TYPES_SIDE_BY_SIDE, GooFluidTests::placedTypesStaySideBySide);
        reg(r, FLUID_FIELDS_STAMPED, GooFluidTests::fluidFieldsReadStampedType);
    }

    private static void registerGooItemTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, ITEM_THROWN_BLOBS_OWN_TYPE, GooItemTests::thrownBlobsLandOwnType);
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
    }

    private static void registerGasketTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, PUSHER_EMPTY_RESERVOIR, GasketPusherTests::emptyReservoirSkipsTick);
        reg(r, PUSHER_NO_PARTNER, GasketPusherTests::noPartnerSkipsTick);
        reg(r, PUSHER_DISPOSE_AND_TICK, GasketPusherTests::disposeAndTickIsSafe);
        reg(r, PUSHER_DOUBLE_DISPOSE, GasketPusherTests::doubleDisposeIsSafe);
        reg(r, PUSHER_REACTOR_OUTPUT_PUSH, GasketPusherTests::reactorOutputPushesToLinkedReceiver);
        reg(r, PUSHER_REACTOR_OUTPUT_REMOVAL, GasketPusherTests::reactorOutputRemovalStopsPush);
        reg(r, CRUCIBLE_ROLE_TRANSMITTER, GasketHolderTests::crucibleResolveRoleAlwaysTransmitter);
        reg(r, CRUCIBLE_NO_GASKET, GasketHolderTests::crucibleNoGasketUnsupported);
        reg(r, CRUCIBLE_WITH_GASKET, GasketHolderTests::crucibleWithGasketSupported);
        reg(r, VAT_SUPPORTS_ROLE, GasketHolderTests::vatSupportsRoleMatchesBlockstate);
        reg(r, HUB_HAS_INTAKE, GasketHolderTests::hubHasIntake);
        reg(r, DEFAULT_ALLOWS_TUNING, GasketHolderTests::defaultAllowsTuningIsTrue);
        reg(r, REACTOR_GASKET_INSTALL, GasketHolderTests::reactorGasketInstallsOnOutputCanister);
        reg(r, REACTOR_TUNER_LINK, GasketHolderTests::reactorTunerLinksCrucibleToOutputCanister);
        reg(r, REACTOR_GASKET_LOCATION, GasketHolderTests::reactorOutputGasketLocationFollowsCanister);
        reg(r, REACTOR_SEATED_GASKET_METADATA, GasketHolderTests::reactorSeatedCanisterAnswersGasketMetadata);
    }

    private static void registerEffectExecutorTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, FX_BLAZE, EffectExecutorTests::blazeMinesBlock);
        reg(r, FX_ROCK, EffectExecutorTests::rockMinesBlock);
        reg(r, FX_FROST, EffectExecutorTests::frostRuns);
        reg(r, FX_METAL, EffectExecutorTests::metalRuns);
        reg(r, FX_CRYSTAL, EffectExecutorTests::crystalRuns);
        reg(r, FX_NETHER, EffectExecutorTests::netherImplodes);
        reg(r, FX_UNSTABLE, EffectExecutorTests::unstableExplodes);
        reg(r, FX_GLOW_WALL, EffectExecutorTests::glowWallLegacy);
        reg(r, FX_GLOW_FLOOR, EffectExecutorTests::glowFloorLegacy);
        reg(r, FX_PROGRAM_GLOW_WALL, EffectExecutorTests::programGlowWall);
        reg(r, FX_PROGRAM_GLOW_FLOOR, EffectExecutorTests::programGlowFloor);
        reg(r, FX_ABILITY_BLAZE, EffectExecutorTests::abilityBlazeTunnel);
        reg(r, FX_ABILITY_ROCK, EffectExecutorTests::abilityRockTunnel);
        reg(r, FX_ABILITY_FROST, EffectExecutorTests::abilityFrostSphere);
        reg(r, FX_PROGRAM_INSTANT, EffectExecutorTests::programInstantDetonation);
        reg(r, FX_PROGRAM_TIMED, EffectExecutorTests::programTimedBomb);
        reg(r, FX_PROGRAM_MINE, EffectExecutorTests::programProximityMine);
        reg(r, FX_PROGRAM_METAL_SPIKES, EffectExecutorTests::programMetalSpikes);
        reg(r, FX_PROGRAM_CRYSTAL_CLOUD, EffectExecutorTests::programCrystalCloud);
        reg(r, FX_PROGRAM_NETHER_BLACK_HOLE, EffectExecutorTests::programNetherBlackHole);
    }

    private static void registerMachineInteractionTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, IX_TAP_VALVE, MachineInteractionTests::tapCanisterInsert);
        reg(r, IX_VAT_GASKET, MachineInteractionTests::vatGasketApply);
        reg(r, IX_HUB_INSERT, MachineInteractionTests::hubCanisterInsert);
        reg(r, IX_HUB_PICKUP, MachineInteractionTests::hubCanisterPickup);
        reg(r, IX_PLEXER_TARGET, MachineInteractionTests::plexerSetTarget);
        reg(r, IX_CRUCIBLE_FUEL, MachineInteractionTests::crucibleFuelInsert);
    }

    private static void registerCrucibleTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, CR_BLOB_INSERT, CrucibleTests::blobInsertViaInteraction);
        reg(r, CR_ITEM_ABSORB, CrucibleTests::itemEntityAbsorption);
    }

    private static void registerPlacementTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, PL_BLAZE, PlacementTests::blazePlacesMarker);
        reg(r, PL_ROCK, PlacementTests::rockPlacesMarker);
        reg(r, PL_FROST, PlacementTests::frostPlacesMarker);
        reg(r, PL_DOUBLE_STACK, PlacementTests::doubleHitStacks);
        reg(r, PL_SIDEWAYS_NEIGHBOR, PlacementTests::sidewaysMarkerSurvivesNeighborChange);
        reg(r, PL_OTHER_TYPES, PlacementTests::otherTypesPlaceMarker);
    }

    private static void registerCanisterInteractionTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, IX_CANISTER_SHIFT_INSERT, CanisterInteractionTests::shiftClickInserts);
        reg(r, IX_CANISTER_CLICK_PICKUP, CanisterInteractionTests::clickPicksUp);
        reg(r, IX_CANISTER_LAST_PICKUP, CanisterInteractionTests::lastPickupRemovesBlock);
        reg(r, IX_CANISTER_EMPTY_HAND, CanisterInteractionTests::emptyHandPicksUp);
    }

    private static void registerMachineTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, MACHINE_CANISTER_INSERT, MachineTests::canisterInsertCreatesHandler);
        reg(r, MACHINE_CANISTER_REMOVE, MachineTests::canisterRemoveClearsHandler);
        reg(r, MACHINE_CANISTER_TICK, MachineTests::canisterTicksWithSlot);
        reg(r, MACHINE_CANISTER_BREAK, MachineTests::canisterBreakWithSlotIsSafe);
        reg(r, MACHINE_CANISTER_FLUID, MachineTests::canisterFluidInsertExtract);
        reg(r, MACHINE_CANISTER_ROUTING, MachineTests::canisterFluidRouting);
        reg(r, MACHINE_CANISTER_ROUNDTRIP, MachineTests::canisterSurvivesRoundTrip);
        reg(r, MACHINE_REACTOR_IDLE, MachineTests::reactorIdleTick);
        reg(r, MACHINE_REACTOR_BREAK, MachineTests::reactorBreakIsSafe);
        reg(r, MACHINE_REACTOR_REACTION, MachineTests::reactorProcessesReaction);
        reg(r, MACHINE_REACTOR_REDSTONE, MachineTests::reactorRedstoneHalts);
        reg(r, MACHINE_PLEXER_IDLE, MachineTests::plexerIdleTick);
    }

    private static void registerMobEffectTests(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> r) {
        reg(r, MOB_METAL, MobEffectTests::metalJavelin);
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
