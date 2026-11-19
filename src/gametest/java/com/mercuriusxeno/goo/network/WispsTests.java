package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityRegistrySource;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.entity.DriftingWisp;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Glow's Wisps through the real landing its stream's drips take, in a
 * sealed dark stone room: a drip striking the floor lays a one-layer Wisps
 * cell shining at light 1, a full cell shines at 15, and a zombie standing
 * on a full cell burns; in a dark corridor a full cell sheds wisps that
 * settle in its air. A drip striking a wall or an underside holds there as
 * a film, which glows by its layer and sheds wisps from where it holds.
 * glow-world-goo-is-named-wisps-and-takes-the-holds-place
 * wisps-goo-glows-at-a-light-level-equal-to-its-layers
 * glow-goo-is-adhesion-four
 * wisps-goo-sheds-a-wisp-toward-nearby-dark
 */
@RegistersGameTests
public final class WispsTests {

    private static final String WISPS_CELL_SHINES_AT_ITS_LAYERS = "wisps_cell_shines_at_its_layers";
    private static final Identifier WISPS = Identifier.parse("goo:glow_wisps");
    /** The room spans 0 to 4 across and 0 to 4 up, stone but for the shaft over the floor block. */
    private static final int ROOM_EDGE = 4;
    /** The floor block the drips strike, in the middle of the room. */
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** The cell over the floor block, where the drips' layers land. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold glow_wisps";
    private static final String CELL_WRONG = "The cell over the floor should hold %d layers of Wisps, stands %s";
    private static final String LIGHT_WRONG = "A Wisps cell of %d layers should shine at block light %d, reads %d";
    private static final String WISPS_BURNS_A_ZOMBIE_ON_IT = "wisps_burns_a_zombie_on_it";
    private static final String ZOMBIE_UNLIT = "A zombie on a full Wisps cell should be on fire";
    private static final String ZOMBIE_UNHARMED = "A zombie on a full Wisps cell should take holy damage";
    private static final String WISPS_HOLY_DAMAGE_HURTS_A_WITHER_SKELETON = "wisps_holy_damage_hurts_a_wither_skeleton";
    /** The tall shaft's air runs over the cell up to here, under a stone roof one higher. */
    private static final int TALL_SHAFT_TOP = 4;
    private static final String SKELETON_UNHARMED =
            "A fire-immune wither skeleton on a full Wisps cell should take holy damage";
    private static final String WISPS_SHEDS_A_WISP_INTO_THE_DARK = "wisps_sheds_a_wisp_into_the_dark";
    /** The corridor's air runs 1 to 30 along x, 1 to 2 up, at z 3, inside the march bay. */
    private static final int CORRIDOR_LENGTH = 30;
    private static final int CORRIDOR_HEIGHT = 2;
    private static final int CORRIDOR_Z = 3;
    /** The full Wisps cell, on the floor at the corridor's near end. */
    private static final BlockPos CORRIDOR_CELL = new BlockPos(1, 1, CORRIDOR_Z);
    private static final String WISPS_DRIFTING_WISP_STANDS_ON_ITS_SPOT =
            "wisps_drifting_wisp_stands_on_its_spot_as_it_settles";
    private static final String WISPS_EVERY_DRIFTING_WISP_SETTLES = "wisps_every_drifting_wisp_settles";
    private static final String WISPS_SHED_WISP_SETTLES_WITHIN_A_SECOND =
            "wisps_shed_wisp_settles_within_a_second_of_leaving";
    /** Ticks a shed wisp has, from leaving the goo, to stand as a wisp block on its spot: a second. */
    private static final int SETTLES_WITHIN_TICKS = 20;
    private static final String SETTLED_LATE = "A wisp left the goo on tick %d and no wisp block stood in the"
            + " corridor by tick %d";
    /** Ticks the corridor sheds for, several sheds of a full cell, before the test succeeds. */
    private static final int SHEDDING_TICKS = 400;
    /** The cave spans 0 to 15 across and 0 to 4 up inside the light bay, stone shell around its air. */
    private static final int CAVE_EDGE = 15;
    private static final int CAVE_TOP = 4;
    /** The full Wisps cell, on the cave's floor in a corner. */
    private static final BlockPos CAVE_CELL = new BlockPos(1, 1, 1);
    private static final String VANISHED = "A drifting wisp left %s with no wisp block settled there";
    private static final String SETTLED_ALONE = "The wisp block at %s appeared with no drifting wisp on its spot";
    private static final String NO_WISP_SETTLED = "A wisp shed by the Wisps cell should settle in the corridor's air";
    private static final String WISPS_DRIP_ON_A_WALL_HOLDS_A_FILM = "wisps_drip_on_a_wall_holds_a_film";
    private static final String WISPS_DRIP_UNDER_A_CEILING_HOLDS_A_FILM = "wisps_drip_under_a_ceiling_holds_a_film";
    /** A stone block hung in the test bay's open air, its west face and underside the drips strike. */
    private static final BlockPos HUNG_BLOCK = new BlockPos(3, 4, 2);
    private static final String NOT_ON_THE_WALL = "A Wisps drip on a wall should leave a film on the cell's east side,"
            + " stands %s";
    private static final String NOT_ON_THE_CEILING = "A Wisps drip under a ceiling should leave a film on the cell's"
            + " ceiling, stands %s";
    private static final String WISPS_CELL_GLOWS_BY_ITS_FILMS_AND_FILL = "wisps_cell_glows_by_its_films_and_fill";
    /** A ceiling film hung in a stone pocket over a bottom slab, a block with no top to stand on and no air. */
    private static final BlockPos POCKET_CEILING_FILM = new BlockPos(1, 2, 1);
    /** A cell of fill and a north wall film on the floor of a stone pocket with no air. */
    private static final BlockPos POCKET_FILL_AND_FILM = new BlockPos(3, 1, 3);
    /** Three layers of fill and one wall film, four layers in all. */
    private static final int FILL_AND_FILM_LAYERS = 4;
    private static final String FILM_LIGHT_WRONG = "A Wisps cell %s holding %d layers should shine at block light"
            + " %d, reads %d";
    private static final String WISPS_CEILING_FILM_SHEDS_A_WISP_AND_EMPTIES =
            "wisps_ceiling_film_sheds_a_wisp_and_empties";
    private static final String WISPS_WALL_FILM_SHEDS_A_WISP_AND_EMPTIES = "wisps_wall_film_sheds_a_wisp_and_empties";
    /** The film cell, under the corridor's roof at its near end, over the corridor's air. */
    private static final BlockPos CORRIDOR_FILM = new BlockPos(1, CORRIDOR_HEIGHT, CORRIDOR_Z);
    /** How far a one-layer cell's shed wisp may head, one block past its layers. */
    private static final int ONE_LAYER_REACH = 2;
    private static final String FILM_WISP_OUT_OF_REACH = "A wisp shed by the film at %s settled at %s, past its reach";
    private static final String CORRIDOR_LIT = "The corridor's air at %s should read dark before the film is laid,"
            + " reads light %d";
    private static final String WISPS_STEP = "wisps";
    private static final String SHED_SPEND_CHANCE = "shed_spend_chance";
    /** A spend chance every draw lands under, so the shed spends the film's layer. */
    private static final double SURE_SPEND = 1.0;
    private static final String WISP_GIVES_WAY_TO_A_DRIP_ON_THE_FLOOR = "wisp_gives_way_to_a_drip_on_the_floor";
    private static final String WISP_GIVES_WAY_TO_A_FILM_ON_A_WALL_AND_A_CEILING =
            "wisp_gives_way_to_a_film_on_a_wall_and_a_ceiling";
    private static final String WISP_GIVES_WAY_TO_GOO_POURING_BESIDE_IT = "wisp_gives_way_to_goo_pouring_beside_it";
    private static final String WISP_GIVES_WAY_TO_GOO_FALLING_ONTO_IT = "wisp_gives_way_to_goo_falling_onto_it";
    private static final Identifier GOOP = Identifier.parse("goo:weird_goop");
    private static final String GOOP_REQUIRED = "The ability registry must hold weird_goop";
    private static final String DRIP_EATEN = "A %s drip landing where a wisp stands should lay one layer, stands %s";
    private static final String POUR_STOPPED = "Goop pouring beside a wisp should take its cell, stands %s";
    private static final String FALL_STOPPED = "Goop falling onto a wisp should take its cell, stands %s";
    /** The full Goop cell pouring sideways, and the floor cell beside it where a wisp stands. */
    private static final BlockPos POURING_CELL = new BlockPos(1, 1, 2);
    private static final BlockPos POURED_INTO = POURING_CELL.east();
    /** The wisp standing on a stone floor, and the Goop cell over it that has nothing to hold it up. */
    private static final BlockPos WISP_UNDER_THE_FALL = new BlockPos(2, 1, 2);
    private static final BlockPos FALLING_CELL = WISP_UNDER_THE_FALL.above();

    private WispsTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, WISPS_CELL_SHINES_AT_ITS_LAYERS,
                WispsTests::wispsCellShinesAtItsLayers);
        GooTestFunctions.registerTest(registrar, WISPS_BURNS_A_ZOMBIE_ON_IT, WispsTests::wispsBurnsAZombieOnIt);
        GooTestFunctions.registerTest(registrar, WISPS_HOLY_DAMAGE_HURTS_A_WITHER_SKELETON,
                WispsTests::wispsHolyDamageHurtsAWitherSkeleton);
        GooTestFunctions.registerTest(registrar, WISPS_SHEDS_A_WISP_INTO_THE_DARK,
                WispsTests::wispsShedsAWispIntoTheDark);
        GooTestFunctions.registerTest(registrar, WISPS_EVERY_DRIFTING_WISP_SETTLES,
                WispsTests::wispsEveryDriftingWispSettles);
        GooTestFunctions.registerTest(registrar, WISPS_SHED_WISP_SETTLES_WITHIN_A_SECOND,
                WispsTests::wispsShedWispSettlesWithinASecondOfLeaving);
        GooTestFunctions.registerTest(registrar, WISPS_DRIFTING_WISP_STANDS_ON_ITS_SPOT,
                WispsTests::wispsDriftingWispStandsOnItsSpotAsItSettles);
        GooTestFunctions.registerTest(registrar, WISPS_DRIP_ON_A_WALL_HOLDS_A_FILM,
                WispsTests::wispsDripOnAWallHoldsAFilm);
        GooTestFunctions.registerTest(registrar, WISPS_DRIP_UNDER_A_CEILING_HOLDS_A_FILM,
                WispsTests::wispsDripUnderACeilingHoldsAFilm);
        GooTestFunctions.registerTest(registrar, WISPS_CELL_GLOWS_BY_ITS_FILMS_AND_FILL,
                WispsTests::wispsCellGlowsByItsFilmsAndFill);
        GooTestFunctions.registerTest(registrar, WISPS_CEILING_FILM_SHEDS_A_WISP_AND_EMPTIES,
                helper -> wispsFilmShedsAWispAndEmpties(helper, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                        .setValue(VolumeCellBlock.CEILING, true)));
        GooTestFunctions.registerTest(registrar, WISPS_WALL_FILM_SHEDS_A_WISP_AND_EMPTIES,
                helper -> wispsFilmShedsAWispAndEmpties(helper, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                        .setValue(VolumeCellBlock.NORTH, SideCoat.ATTACHED)));
        GooTestFunctions.registerTest(registrar, WISP_GIVES_WAY_TO_A_DRIP_ON_THE_FLOOR,
                WispsTests::wispGivesWayToADripOnTheFloor);
        GooTestFunctions.registerTest(registrar, WISP_GIVES_WAY_TO_A_FILM_ON_A_WALL_AND_A_CEILING,
                WispsTests::wispGivesWayToAFilmOnAWallAndACeiling);
        GooTestFunctions.registerTest(registrar, WISP_GIVES_WAY_TO_GOO_POURING_BESIDE_IT,
                WispsTests::wispGivesWayToGooPouringBesideIt);
        GooTestFunctions.registerTest(registrar, WISP_GIVES_WAY_TO_GOO_FALLING_ONTO_IT,
                WispsTests::wispGivesWayToGooFallingOntoIt);
    }

    /**
     * A drip landing on a floor cell where a wisp stands lays its one layer
     * there, a Wisps drip and a Goop drip alike: the wisp gives way to the goo.
     * every-block-displaces-wisps
     *
     * @param helper the gametest helper
     */
    public static void wispGivesWayToADripOnTheFloor(GameTestHelper helper) {
        sealRoom(helper);
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.WISP.get());
        landOneDripOnTheFloor(helper, GooTypes.GLOW, WISPS);
        assertOneLayerOver(helper, GooBlocks.GLOW_WISPS.get(), "Wisps");
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.WISP.get());
        landOneDripOnTheFloor(helper, GooTypes.WEIRD, GOOP);
        assertOneLayerOver(helper, GooBlocks.SLIME_COAT.get(), "Goop");
        helper.succeed();
    }

    private static void landOneDripOnTheFloor(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
                                              Identifier abilityId) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(abilityId);
        helper.assertTrue(ability != null, abilityId.equals(GOOP) ? GOOP_REQUIRED : ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, gooType);
        KnownRecipes.teachRequires(caster, ability);
        LandedDrips.landOn(helper, caster, ability, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
    }

    private static void assertOneLayerOver(GameTestHelper helper, Block cell, String goo) {
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean laid = state.is(cell) && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(laid, String.format(DRIP_EATEN, goo, state));
    }

    /**
     * A Wisps drip striking a wall face, and one striking a ceiling face,
     * each hold their film in the cell beside that face where a wisp stands.
     * every-block-displaces-wisps
     *
     * @param helper the gametest helper
     */
    public static void wispGivesWayToAFilmOnAWallAndACeiling(GameTestHelper helper) {
        hangABlock(helper);
        helper.setBlock(HUNG_BLOCK.west(), GooBlocks.WISP.get());
        helper.setBlock(HUNG_BLOCK.below(), GooBlocks.WISP.get());
        landOneDripOnTheHungBlock(helper, Direction.WEST);
        landOneDripOnTheHungBlock(helper, Direction.DOWN);
        BlockState wall = helper.getBlockState(HUNG_BLOCK.west());
        helper.assertTrue(wall.is(GooBlocks.GLOW_WISPS.get())
                && VolumeCellBlock.coatOn(wall, Direction.EAST) == SideCoat.ATTACHED, String.format(NOT_ON_THE_WALL,
                wall));
        BlockState ceiling = helper.getBlockState(HUNG_BLOCK.below());
        helper.assertTrue(ceiling.is(GooBlocks.GLOW_WISPS.get()) && ceiling.getValue(VolumeCellBlock.CEILING),
                String.format(NOT_ON_THE_CEILING, ceiling));
        helper.succeed();
    }

    /**
     * A full Goop cell walled in with one floor cell beside it, where a wisp
     * stands, pours its layers into that cell.
     * every-block-displaces-wisps
     *
     * @param helper the gametest helper
     */
    public static void wispGivesWayToGooPouringBesideIt(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= 2; y++) {
                for (int z = 1; z <= 3; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(POURED_INTO, GooBlocks.WISP.get());
        helper.setBlock(POURING_CELL, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        helper.succeedWhen(() -> {
            BlockState poured = helper.getBlockState(POURED_INTO);
            helper.assertTrue(poured.is(GooBlocks.SLIME_COAT.get()), String.format(POUR_STOPPED, poured));
        });
    }

    /**
     * A Goop cell set over a wisp standing on a stone floor, with nothing
     * beside it to hold it, falls, and its layer takes the wisp's cell.
     * every-block-displaces-wisps
     *
     * @param helper the gametest helper
     */
    public static void wispGivesWayToGooFallingOntoIt(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        helper.setBlock(WISP_UNDER_THE_FALL, GooBlocks.WISP.get());
        helper.setBlock(FALLING_CELL, GooBlocks.SLIME_COAT.get().defaultBlockState());
        helper.succeedWhen(() -> {
            BlockState landed = helper.getBlockState(WISP_UNDER_THE_FALL);
            helper.assertTrue(landed.is(GooBlocks.SLIME_COAT.get()), String.format(FALL_STOPPED, landed));
        });
    }

    /**
     * One Wisps drip striking the west face of a stone block hung in open
     * air leaves a Wisps cell beside that face holding a film on its east
     * side, the side facing the wall: glow's adhesion holds where it strikes.
     * glow-goo-is-adhesion-four
     *
     * @param helper the gametest helper
     */
    public static void wispsDripOnAWallHoldsAFilm(GameTestHelper helper) {
        hangABlock(helper);
        landOneDripOnTheHungBlock(helper, Direction.WEST);
        BlockState cell = helper.getBlockState(HUNG_BLOCK.west());
        boolean filmed = cell.is(GooBlocks.GLOW_WISPS.get())
                && VolumeCellBlock.coatOn(cell, Direction.EAST) == SideCoat.ATTACHED;
        helper.assertTrue(filmed, String.format(NOT_ON_THE_WALL, cell));
        helper.succeed();
    }

    /**
     * One Wisps drip striking the underside of a stone block hung in open
     * air, no wall beside the cell under it, leaves a Wisps cell there
     * holding a film on its ceiling: glow's adhesion holds an underside.
     * glow-goo-is-adhesion-four
     *
     * @param helper the gametest helper
     */
    public static void wispsDripUnderACeilingHoldsAFilm(GameTestHelper helper) {
        hangABlock(helper);
        landOneDripOnTheHungBlock(helper, Direction.DOWN);
        BlockState cell = helper.getBlockState(HUNG_BLOCK.below());
        boolean filmed = cell.is(GooBlocks.GLOW_WISPS.get()) && cell.getValue(VolumeCellBlock.CEILING);
        helper.assertTrue(filmed, String.format(NOT_ON_THE_CEILING, cell));
        helper.succeed();
    }

    // Hangs a stone block in the bay's open air, every cell around the cells beside its west face and under it open.
    private static void hangABlock(GameTestHelper helper) {
        for (int x = HUNG_BLOCK.getX() - 2; x <= HUNG_BLOCK.getX() + 1; x++) {
            for (int y = 1; y <= HUNG_BLOCK.getY(); y++) {
                for (int z = HUNG_BLOCK.getZ() - 1; z <= HUNG_BLOCK.getZ() + 1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.setBlock(HUNG_BLOCK, Blocks.STONE);
    }

    // Lands one Wisps drip on a face of the hung block from a glow-holding caster, who pays for its layer.
    private static void landOneDripOnTheHungBlock(GameTestHelper helper, Direction face) {
        AbilityDefinition wisps = AbilityRegistry.of(helper.getLevel()).getAbility(WISPS);
        helper.assertTrue(wisps != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.GLOW);
        KnownRecipes.teachRequires(caster, wisps);
        LandedDrips.landOnSide(helper, caster, wisps, HUNG_BLOCK, face, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
    }

    /**
     * A Wisps cell shines at every layer it keeps, fill and films alike: in
     * a sealed stone pocket a cell holding one ceiling film and no fill
     * shines at block light 1, and a cell of three layers of fill and one
     * wall film shines at 4. Neither pocket holds air, so neither cell sheds
     * a wisp and spends a layer while the light is read.
     * wisps-goo-glows-at-a-light-level-equal-to-its-layers
     *
     * @param helper the gametest helper
     */
    public static void wispsCellGlowsByItsFilmsAndFill(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= ROOM_EDGE; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(POCKET_CEILING_FILM.below(), Blocks.STONE_SLAB.defaultBlockState());
        helper.setBlock(POCKET_CEILING_FILM, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.CEILING, true));
        helper.setBlock(POCKET_FILL_AND_FILM, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, FILL_AND_FILM_LAYERS)
                .setValue(VolumeCellBlock.NORTH, SideCoat.ATTACHED));
        helper.succeedWhen(() -> {
            assertFilmShinesAt(helper, POCKET_CEILING_FILM, 1);
            assertFilmShinesAt(helper, POCKET_FILL_AND_FILM, FILL_AND_FILM_LAYERS);
        });
    }

    private static void assertFilmShinesAt(GameTestHelper helper, BlockPos cell, int layers) {
        BlockState state = helper.getBlockState(cell);
        int held = state.is(GooBlocks.GLOW_WISPS.get()) ? VolumeCellBlock.layersHeld(state) : 0;
        int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(cell));
        helper.assertTrue(held == layers && light == layers, String.format(FILM_LIGHT_WRONG, state, held, layers,
                light));
    }

    /**
     * A Wisps cell holding one film and no fill, hung under the roof of a
     * sealed dark corridor over its air, sheds a wisp that settles as a wisp
     * block within its reach, and the shed spending its one layer leaves the
     * cell standing as air. The film is laid once the corridor reads dark,
     * and its own tick runs at once with the shed's spend held sure, so the
     * one shed the test reads spends the layer on that tick.
     * wisps-goo-sheds-a-wisp-toward-nearby-dark
     * gametests-waiting-on-a-random-draw-are-found-and-made-deterministic
     *
     * @param helper the gametest helper
     * @param film   the film cell's state, one layer on the ceiling or a wall
     */
    public static void wispsFilmShedsAWispAndEmpties(GameTestHelper helper, BlockState film) {
        sealCorridor(helper);
        helper.startSequence()
                .thenWaitUntil(() -> assertDarkInReachOfTheFilm(helper))
                .thenExecute(() -> {
                    shedTheFilmAtASureSpend(helper, film);
                    helper.assertTrue(helper.getBlockState(CORRIDOR_FILM).isAir(),
                            String.format(CELL_WRONG, 0, helper.getBlockState(CORRIDOR_FILM)));
                })
                .thenWaitUntil(() -> {
                    List<BlockPos> wisps = wispsInTheCorridor(helper);
                    helper.assertTrue(!wisps.isEmpty(), NO_WISP_SETTLED);
                    for (BlockPos wisp : wisps) {
                        helper.assertTrue(wisp.distManhattan(CORRIDOR_FILM) <= ONE_LAYER_REACH,
                                String.format(FILM_WISP_OUT_OF_REACH, CORRIDOR_FILM, wisp));
                    }
                })
                .thenSucceed();
    }

    private static void assertDarkInReachOfTheFilm(GameTestHelper helper) {
        for (int x = 1; x <= CORRIDOR_FILM.getX() + ONE_LAYER_REACH; x++) {
            for (int y = 1; y <= CORRIDOR_HEIGHT; y++) {
                BlockPos air = new BlockPos(x, y, CORRIDOR_Z);
                int light = helper.getLevel().getMaxLocalRawBrightness(helper.absolutePos(air));
                helper.assertTrue(light < GooConfig.wispsLightThreshold(), String.format(CORRIDOR_LIT, air, light));
            }
        }
    }

    // Lays the film and runs the cell's own tick at once, the shed's spend chance held sure for that tick alone.
    private static void shedTheFilmAtASureSpend(GameTestHelper helper, BlockState film) {
        helper.setBlock(CORRIDOR_FILM, film);
        BlockPos cellPos = helper.absolutePos(CORRIDOR_FILM);
        BlockState cell = helper.getLevel().getBlockState(cellPos);
        AbilityRegistrySource held = (AbilityRegistrySource) helper.getLevel().getServer().getServerResources()
                .managers();
        AbilityRegistry shipped = held.abilityRegistry();
        held.holdAbilityRegistry(HeldAbilitySteps.withStepFields(shipped, WISPS, WISPS_STEP,
                Map.of(SHED_SPEND_CHANCE, SURE_SPEND)));
        try {
            cell.tick(helper.getLevel(), cellPos, helper.getLevel().getRandom());
        } finally {
            held.holdAbilityRegistry(shipped);
        }
    }

    /**
     * A full Wisps cell at one end of a sealed dark stone corridor sheds
     * wisps that drift off down it and settle as wisp blocks in its air.
     * wisps-goo-sheds-a-wisp-toward-nearby-dark
     * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
     *
     * @param helper the gametest helper
     */
    public static void wispsShedsAWispIntoTheDark(GameTestHelper helper) {
        sealCorridorWithAFullCell(helper);
        helper.succeedWhen(() -> helper.assertTrue(!wispsInTheCorridor(helper).isEmpty(), NO_WISP_SETTLED));
    }

    /**
     * A full Wisps cell at one end of a sealed dark corridor sheds a wisp,
     * and a wisp block stands in the corridor's air within a second of the
     * drifting wisp leaving the goo: the wisp beams to its spot in about
     * half a second, wherever the spot lies.
     * shed-wisps-arrive-in-about-half-a-second
     *
     * @param helper the gametest helper
     */
    public static void wispsShedWispSettlesWithinASecondOfLeaving(GameTestHelper helper) {
        sealCorridorWithAFullCell(helper);
        AABB corridor = new AABB(helper.absolutePos(new BlockPos(0, 0, CORRIDOR_Z - 1)))
                .expandTowards(CORRIDOR_LENGTH + 1, CORRIDOR_HEIGHT + 1, 2);
        long[] leftAt = {-1};
        helper.onEachTick(() -> {
            if (leftAt[0] < 0 && !helper.getLevel().getEntitiesOfClass(DriftingWisp.class, corridor).isEmpty()) {
                leftAt[0] = helper.getTick();
            }
            if (leftAt[0] >= 0 && wispsInTheCorridor(helper).isEmpty()) {
                helper.assertTrue(helper.getTick() - leftAt[0] < SETTLES_WITHIN_TICKS,
                        String.format(SETTLED_LATE, leftAt[0], leftAt[0] + SETTLES_WITHIN_TICKS));
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(!wispsInTheCorridor(helper).isEmpty(), NO_WISP_SETTLED));
    }

    /**
     * A drifting wisp still stands on its spot on the tick its wisp block
     * appears there, so a watching client never meets the spot with neither
     * the drifting mote nor the settled one drawn.
     * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
     *
     * @param helper the gametest helper
     */
    public static void wispsDriftingWispStandsOnItsSpotAsItSettles(GameTestHelper helper) {
        sealCorridorWithAFullCell(helper);
        Set<BlockPos> settled = new HashSet<>();
        helper.onEachTick(() -> {
            for (BlockPos wisp : wispsInTheCorridor(helper)) {
                if (settled.add(wisp)) {
                    int drifting = helper.getLevel().getEntitiesOfClass(DriftingWisp.class,
                            new AABB(helper.absolutePos(wisp))).size();
                    helper.assertTrue(drifting > 0, String.format(SETTLED_ALONE, wisp));
                }
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(!settled.isEmpty(), NO_WISP_SETTLED));
    }

    /**
     * Every wisp a full cell sheds into a sealed dark cave ends settled: no
     * drifting wisp leaves the world but onto a wisp block, so none heads
     * for a spot another wisp's light will reach first.
     * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
     *
     * @param helper the gametest helper
     */
    public static void wispsEveryDriftingWispSettles(GameTestHelper helper) {
        for (int x = 0; x <= CAVE_EDGE; x++) {
            for (int y = 0; y <= CAVE_TOP; y++) {
                for (int z = 0; z <= CAVE_EDGE; z++) {
                    boolean shell = x == 0 || x == CAVE_EDGE || y == 0 || y == CAVE_TOP || z == 0 || z == CAVE_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
        helper.setBlock(CAVE_CELL, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Map<UUID, BlockPos> drifting = new HashMap<>();
        AABB cave = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(CAVE_EDGE, CAVE_TOP, CAVE_EDGE);
        helper.onEachTick(() -> {
            Map<UUID, BlockPos> now = new HashMap<>();
            for (DriftingWisp wisp : helper.getLevel().getEntitiesOfClass(DriftingWisp.class, cave)) {
                now.put(wisp.getUUID(), wisp.blockPosition());
            }
            drifting.forEach((id, last) -> {
                if (!now.containsKey(id)) {
                    helper.assertTrue(helper.getLevel().getBlockState(last).is(GooBlocks.WISP.get()),
                            String.format(VANISHED, helper.relativePos(last)));
                }
            });
            drifting.clear();
            drifting.putAll(now);
        });
        helper.runAtTickTime(SHEDDING_TICKS, helper::succeed);
    }

    private static void sealCorridorWithAFullCell(GameTestHelper helper) {
        sealCorridor(helper);
        helper.setBlock(CORRIDOR_CELL, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
    }

    private static void sealCorridor(GameTestHelper helper) {
        for (int x = 0; x <= CORRIDOR_LENGTH + 1; x++) {
            for (int y = 0; y <= CORRIDOR_HEIGHT + 1; y++) {
                for (int z = CORRIDOR_Z - 1; z <= CORRIDOR_Z + 1; z++) {
                    boolean air = x >= 1 && x <= CORRIDOR_LENGTH && y >= 1 && y <= CORRIDOR_HEIGHT && z == CORRIDOR_Z;
                    helper.setBlock(new BlockPos(x, y, z), air ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
    }

    private static List<BlockPos> wispsInTheCorridor(GameTestHelper helper) {
        List<BlockPos> wisps = new ArrayList<>();
        for (int x = 1; x <= CORRIDOR_LENGTH; x++) {
            for (int y = 1; y <= CORRIDOR_HEIGHT; y++) {
                BlockPos air = new BlockPos(x, y, CORRIDOR_Z);
                if (helper.getBlockState(air).is(GooBlocks.WISP.get())) {
                    wisps.add(air);
                }
            }
        }
        return wisps;
    }

    /**
     * A zombie standing on a full Wisps cell, roofed from the sun in the
     * stone shaft, is set on fire and its health falls once the holy
     * damage's cadence comes round.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     *
     * @param helper the gametest helper
     */
    public static void wispsBurnsAZombieOnIt(GameTestHelper helper) {
        sealRoom(helper);
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, OVER_THE_FLOOR.above());
        float startingHealth = zombie.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.isOnFire(), ZOMBIE_UNLIT);
            helper.assertTrue(zombie.getHealth() < startingHealth, ZOMBIE_UNHARMED);
        });
    }

    /**
     * A wither skeleton standing on a full Wisps cell, in a stone shaft tall
     * enough to hold it and roofed from the sun, loses health once the holy
     * damage's cadence comes round: fire cannot harm it, so the health it
     * loses is goo:holy's.
     * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
     *
     * @param helper the gametest helper
     */
    public static void wispsHolyDamageHurtsAWitherSkeleton(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= TALL_SHAFT_TOP + 1; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shaft = x == FLOOR.getX() && z == FLOOR.getZ() && y > FLOOR.getY() && y <= TALL_SHAFT_TOP;
                    helper.setBlock(new BlockPos(x, y, z), shaft ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.GLOW_WISPS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        WitherSkeleton skeleton = helper.spawn(EntityType.WITHER_SKELETON, OVER_THE_FLOOR.above());
        float startingHealth = skeleton.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(skeleton.getHealth() < startingHealth, SKELETON_UNHARMED));
    }

    /**
     * One Wisps drip striking the floor of a sealed dark room lays a Wisps
     * cell of one layer shining at block light 1; fourteen more fill the
     * cell, and it shines at 15. The light engine runs off the server
     * thread and lit a full cell nine ticks late under a loaded suite, so
     * each read waits until the light answers, within the test's ticks.
     *
     * @param helper the gametest helper
     */
    public static void wispsCellShinesAtItsLayers(GameTestHelper helper) {
        sealRoom(helper);
        AbilityDefinition wisps = AbilityRegistry.of(helper.getLevel()).getAbility(WISPS);
        helper.assertTrue(wisps != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.GLOW);
        KnownRecipes.teachRequires(caster, wisps);
        LandedDrips.landOn(helper, caster, wisps, FLOOR, 1);
        helper.startSequence()
                .thenWaitUntil(() -> assertShinesAt(helper, 1))
                .thenExecute(() -> LandedDrips.landOn(helper, caster, wisps, FLOOR, VolumeCellBlock.MAX_LAYERS - 1))
                .thenWaitUntil(() -> assertShinesAt(helper, VolumeCellBlock.MAX_LAYERS))
                .thenExecute(() -> helper.getLevel().getServer().getPlayerList().remove(caster))
                .thenSucceed();
    }

    private static void assertShinesAt(GameTestHelper helper, int layers) {
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean held = state.is(GooBlocks.GLOW_WISPS.get()) && state.getValue(VolumeCellBlock.LAYERS) == layers;
        helper.assertTrue(held, String.format(CELL_WRONG, layers, state));
        int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(OVER_THE_FLOOR));
        helper.assertTrue(light == layers, String.format(LIGHT_WRONG, layers, layers, light));
    }

    /**
     * Seals a stone room whose only air is the one-block shaft over the
     * floor block, so the goo has no neighbour to pour into and its layers
     * stack in the cell.
     */
    private static void sealRoom(GameTestHelper helper) {
        for (int x = 0; x <= ROOM_EDGE; x++) {
            for (int y = 0; y <= ROOM_EDGE; y++) {
                for (int z = 0; z <= ROOM_EDGE; z++) {
                    boolean shaft = x == FLOOR.getX() && z == FLOOR.getZ() && y > FLOOR.getY() && y < ROOM_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shaft ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
    }
}
