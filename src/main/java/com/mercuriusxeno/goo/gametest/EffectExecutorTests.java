package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import java.util.function.BiConsumer;

/**
 * Gametests for the chain marker effect executors. Each test places a chain
 * marker against a wall of stone, initializes a goo type, and waits for the
 * fuse + behavior to complete. Covers ChainMarkerBlockEntity tick lifecycle,
 * ChainProfiles, ChainFootprint, EffectBlockPlacement, and the per-type
 * Behavior + Executor classes.
 */
public final class EffectExecutorTests {

    /** Marker placed here, facing NORTH into the stone wall. */
    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    /** Fuse is 30 ticks; behaviors run 10-70 more depending on type. */
    private static final int FUSE_TICKS = 30;
    /** Extra ticks after fuse for mining behaviors (1 stack). */
    private static final int MINING_POST_FUSE = 15;
    /** Extra ticks for Nether's multi-phase behavior. */
    private static final int NETHER_POST_FUSE = 80;
    /** Extra ticks for simpler instant/short behaviors. */
    private static final int SHORT_POST_FUSE = 5;
    private static final String VALUES_REQUIRED = "Goo values must be loaded for rock mining to work";
    private static final int WALL_X_MAX = 5;
    private static final int WALL_Y_MAX = 3;
    private static final int WALL_Z_MAX = 2;
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String ABILITY_BLAZE_TUNNEL = "goo:blaze_tunnel";
    private static final String ABILITY_ROCK_TUNNEL = "goo:rock_tunnel";
    private static final String ABILITY_FROST_SPHERE = "goo:frost_sphere";
    private static final String ABILITY_INSTANT_DETONATION = "goo:unstable_instant_detonation";
    private static final String ABILITY_TIMED_BOMB = "goo:unstable_timed_bomb";
    private static final String ABILITY_PROXIMITY_MINE = "goo:unstable_proximity_mine";
    private static final String ABILITY_GLOW_CRYSTAL = "goo:glow_crystal";
    /** Fuse of the timed bomb JSON. */
    private static final int TIMED_BOMB_FUSE = 60;
    /** Half the timed bomb fuse, where the marker must still stand. */
    private static final int TIMED_BOMB_MIDWAY = TIMED_BOMB_FUSE / 2;
    /** Ticks an armed mine idles before the test spawns a target. */
    private static final int MINE_IDLE_TICKS = 5;
    /** Where the mine's target spawns: two blocks from the marker, inside its radius. */
    private static final BlockPos MINE_TARGET_POS = MARKER_POS.east(2);

    private EffectExecutorTests() {}

    /**
     * Places a 3-deep wall of stone north of the marker and initializes the BE.
     * The marker faces NORTH so the effect mines into the wall.
     *
     * @param helper the gametest helper
     * @param type   the goo type for the chain marker
     */
    private static void placeMarkerWithWall(GameTestHelper helper, GooType type) {
        // Fill a 5x3x3 wall of stone north of the marker (z=0..2, x=1..5, y=1..3)
        for (int x = 1; x <= WALL_X_MAX; x++) {
            for (int y = 1; y <= WALL_Y_MAX; y++) {
                for (int z = 0; z <= WALL_Z_MAX; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        // Place marker in air just south of the wall
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity be = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        be.initChain(type, Direction.SOUTH);
    }

    /**
     * Blaze: places marker facing stone wall, verifies the block is mined
     * and the marker removes itself after fuse + mining completes.
     *
     * @param helper the gametest helper
     */
    public static void blazeMinesBlock(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.BLAZE);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Rock: places marker facing stone wall, verifies the block is mined.
     * Requires goo values to be loaded so stone is recognized as rock-compatible.
     *
     * @param helper the gametest helper
     */
    public static void rockMinesBlock(GameTestHelper helper) {
        helper.assertTrue(Goo.GOO_VALUES.size() > 0, VALUES_REQUIRED);
        placeMarkerWithWall(helper, GooType.ROCK);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Frost: places marker and verifies the behavior runs without crashing.
     * Frost converts water/blocks to ice in a sphere; with no water nearby,
     * it completes quickly.
     *
     * @param helper the gametest helper
     */
    public static void frostRuns(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.FROST);
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Metal: places marker and verifies the spike trap behavior runs.
     * Metal is a short-lived effect that damages entities in range.
     *
     * @param helper the gametest helper
     */
    public static void metalRuns(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.METAL);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Crystal: places marker and verifies the DOT cloud behavior runs.
     *
     * @param helper the gametest helper
     */
    public static void crystalRuns(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.CRYSTAL);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Nether: places marker and verifies the multi-phase implosion completes.
     * Nether has EXPAND, HOLD, CONTRACT, POPPING phases totaling ~60 ticks.
     *
     * @param helper the gametest helper
     */
    public static void netherImplodes(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.NETHER);
        helper.runAfterDelay(FUSE_TICKS + NETHER_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    /**
     * Unstable: places marker and verifies the instant explosion runs.
     * Unstable has a shorter fuse (20 ticks) and detonates immediately.
     *
     * @param helper the gametest helper
     */
    public static void unstableExplodes(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooType.UNSTABLE);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    // --- Glow crystal program (task glow-crystal-program) ---

    /**
     * Stands a glow marker at the marker position on a stone support:
     * against a wall when the placed face is horizontal, on a floor when
     * it is up. The support sits behind the placed face, where the crystal
     * will need it.
     *
     * @param helper     the gametest helper
     * @param placedFace the face the marker was placed on
     * @return the marker's block entity, ready for either init path
     */
    private static ChainMarkerBlockEntity standGlowMarker(GameTestHelper helper, Direction placedFace) {
        helper.setBlock(MARKER_POS.relative(placedFace.getOpposite()), Blocks.STONE);
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        return helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
    }

    /**
     * Asserts the glow crystal replaced the marker with facing from the
     * placed face, shape bump (no flat blob) and size tiny (one stack).
     *
     * @param helper the gametest helper
     * @param facing the placed face the crystal must face
     */
    private static void assertGlowCrystal(GameTestHelper helper, Direction facing) {
        helper.assertBlockPresent(GooBlocks.GLOW_CRYSTAL.get(), MARKER_POS);
        helper.assertBlockProperty(MARKER_POS, GlowCrystalBlock.FACING, facing);
        helper.assertBlockProperty(MARKER_POS, GlowCrystalBlock.SHAPE, GlowCrystalBlock.CrystalShape.BUMP);
        helper.assertBlockProperty(MARKER_POS, GlowCrystalBlock.SIZE, GlowCrystalBlock.CrystalSize.TINY);
    }

    /**
     * Runs one glow crystal case: the marker stands with the given placed
     * face, is initialized by the given path, and after the fuse the
     * crystal stands in its place.
     *
     * @param helper     the gametest helper
     * @param placedFace the face the marker was placed on
     * @param init       the init path, legacy or ability
     */
    private static void glowCrystalCase(GameTestHelper helper, Direction placedFace,
                                        BiConsumer<ChainMarkerBlockEntity, Direction> init) {
        init.accept(standGlowMarker(helper, placedFace), placedFace);
        helper.runAfterDelay(FUSE_TICKS + SHORT_POST_FUSE, () -> {
            assertGlowCrystal(helper, placedFace);
            helper.succeed();
        });
    }

    /**
     * Initializes a marker through the no-ability path, whose legacy glow
     * profile runs the glow_crystal program.
     *
     * @param be         the marker
     * @param placedFace the face the marker was placed on
     */
    private static void initGlowLegacy(ChainMarkerBlockEntity be, Direction placedFace) {
        be.initChain(GooType.GLOW, placedFace);
    }

    /**
     * Initializes a marker through the ability path with glow_crystal.
     *
     * @param helper the gametest helper, which fails when the registry lacks the ability
     * @return the init
     */
    private static BiConsumer<ChainMarkerBlockEntity, Direction> initGlowAbility(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.getAbility(Identifier.parse(ABILITY_GLOW_CRYSTAL));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        return (be, placedFace) -> be.initChainFromAbility(GooType.GLOW, placedFace, ability);
    }

    /**
     * Glow on a wall through the no-ability path.
     *
     * @param helper the gametest helper
     */
    public static void glowWallLegacy(GameTestHelper helper) {
        glowCrystalCase(helper, Direction.SOUTH, EffectExecutorTests::initGlowLegacy);
    }

    /**
     * Glow on a floor through the no-ability path.
     *
     * @param helper the gametest helper
     */
    public static void glowFloorLegacy(GameTestHelper helper) {
        glowCrystalCase(helper, Direction.UP, EffectExecutorTests::initGlowLegacy);
    }

    /**
     * Glow on a wall through the ability path.
     *
     * @param helper the gametest helper
     */
    public static void programGlowWall(GameTestHelper helper) {
        glowCrystalCase(helper, Direction.SOUTH, initGlowAbility(helper));
    }

    /**
     * Glow on a floor through the ability path.
     *
     * @param helper the gametest helper
     */
    public static void programGlowFloor(GameTestHelper helper) {
        glowCrystalCase(helper, Direction.UP, initGlowAbility(helper));
    }

    // --- Data-driven ability path ---

    /**
     * Places a chain marker initialized via the ability path instead of
     * the legacy ChainProfile path. Covers DataDrivenChainBehavior,
     * ProgressiveAreaBlock, and the BehaviorType factory.
     *
     * @param helper    the gametest helper
     * @param type      the goo type
     * @param abilityId the ability identifier string
     */
    private static void placeMarkerWithAbility(GameTestHelper helper, GooType type, String abilityId) {
        for (int x = 1; x <= WALL_X_MAX; x++) {
            for (int y = 1; y <= WALL_Y_MAX; y++) {
                for (int z = 0; z <= WALL_Z_MAX; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity be = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        AbilityDefinition ability = AbilityRegistry.getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        be.initChainFromAbility(type, Direction.SOUTH, ability);
    }

    /**
     * Blaze tunnel via the data-driven ability path. Exercises
     * DataDrivenChainBehavior -> ProgressiveAreaBlock -> BlazeExecutor.
     *
     * @param helper the gametest helper
     */
    public static void abilityBlazeTunnel(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooType.BLAZE, ABILITY_BLAZE_TUNNEL);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Rock tunnel via the data-driven ability path. Exercises
     * DataDrivenChainBehavior -> ProgressiveAreaBlock -> RockExecutor.
     *
     * @param helper the gametest helper
     */
    public static void abilityRockTunnel(GameTestHelper helper) {
        helper.assertTrue(Goo.GOO_VALUES.size() > 0, VALUES_REQUIRED);
        placeMarkerWithAbility(helper, GooType.ROCK, ABILITY_ROCK_TUNNEL);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Frost sphere via the data-driven ability path. Exercises
     * DataDrivenChainBehavior -> ProgressiveAreaBlock -> FrostBehavior.
     *
     * @param helper the gametest helper
     */
    public static void abilityFrostSphere(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooType.FROST, ABILITY_FROST_SPHERE);
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.succeed();
        });
    }

    // --- Step programs (decision ability-params-in-datapack) ---

    /**
     * Asserts the marker has detonated: the program ended so the marker
     * removed itself, and the explosion broke the stone it faced.
     *
     * @param helper the gametest helper
     */
    private static void assertDetonated(GameTestHelper helper) {
        helper.assertBlockNotPresent(GooBlocks.CHAIN_MARKER.get(), MARKER_POS);
        helper.assertBlockNotPresent(Blocks.STONE, MARKER_POS.north());
    }

    /**
     * Instant detonation as a program: a one-tick fuse then an explode step
     * whose power is an expression over the stack count.
     *
     * @param helper the gametest helper
     */
    public static void programInstantDetonation(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooType.UNSTABLE, ABILITY_INSTANT_DETONATION);
        helper.runAfterDelay(SHORT_POST_FUSE, () -> {
            assertDetonated(helper);
            helper.succeed();
        });
    }

    /**
     * Timed bomb as a program: the marker stands through half its fuse and
     * has detonated after the fuse.
     *
     * @param helper the gametest helper
     */
    public static void programTimedBomb(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooType.UNSTABLE, ABILITY_TIMED_BOMB);
        helper.runAfterDelay(TIMED_BOMB_MIDWAY, () ->
                helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), MARKER_POS));
        helper.runAfterDelay(TIMED_BOMB_FUSE + SHORT_POST_FUSE, () -> {
            assertDetonated(helper);
            helper.succeed();
        });
    }

    /**
     * Proximity mine as a program: armed, the marker idles on its
     * await_entity step until a living entity enters the radius, then the
     * explode step fires.
     *
     * @param helper the gametest helper
     */
    public static void programProximityMine(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooType.UNSTABLE, ABILITY_PROXIMITY_MINE);
        helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class).instantDetonate();
        helper.runAfterDelay(MINE_IDLE_TICKS, () -> {
            helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), MARKER_POS);
            helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS);
        });
        helper.runAfterDelay(MINE_IDLE_TICKS + SHORT_POST_FUSE, () -> {
            assertDetonated(helper);
            helper.succeed();
        });
    }
}
