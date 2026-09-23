package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
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
    private static final int WALL_X_MIN = 0;
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
    private static final String ABILITY_METAL_SPIKES = "goo:metal_spikes";
    /**
     * Ticks from a target entering the trap to the check that it was
     * impaled: past the strike's six-tick windup and inside the ten-tick
     * cooldown, so the trap has struck once.
     */
    private static final int SPIKE_STRIKE_WINDOW = 8;
    /** Ticks a sneaking player stands in the trap, past two cooldowns. */
    private static final int SNEAK_TICKS = 20;
    private static final String SPIKE_MISSED = "The metal trap left the walking pig unhurt";
    private static final String SPIKE_HIT_SNEAKER = "The metal trap hurt the sneaking player";
    private static final String STACK_NOT_SPENT = "The metal trap's impale spent no stack";
    private static final String STACK_SPENT_ON_SNEAKER = "The metal trap spent a stack on the sneaking player";
    private static final String ABILITY_CRYSTAL_CLOUD = "goo:crystal_cloud";
    /** Where the crystal test's standing pig stands: two blocks west, inside the cloud's radius. */
    private static final BlockPos STANDING_PIG_POS = MARKER_POS.west(2);
    /** Horizontal speed the moving pig is shuffled at, reversed every tick so it stays put. */
    private static final double SHUFFLE_SPEED = 0.2;
    /** Ticks per back-and-forth of the moving pig's shuffle. */
    private static final int SHUFFLE_PERIOD = 2;
    /** Ticks after the fuse the cloud shreds for, several of its two-tick periods. */
    private static final int SHRED_WINDOW = 12;
    private static final String CLOUD_MISSED_MOVER = "The crystal cloud left the moving pig unhurt";
    private static final String CLOUD_HIT_STANDING = "The crystal cloud hurt the standing pig";
    private static final String ABILITY_NETHER_BLACK_HOLE = "goo:nether_black_hole";
    /** Ticks the black hole expands before it consumes its sphere. */
    private static final int BLACK_HOLE_EXPAND_TICKS = 15;
    /** Ticks from the fuse to the tick the black hole pops: expand, hold and contract. */
    private static final int BLACK_HOLE_LIFE_TICKS = 60;
    private static final float HEALTH_TOLERANCE = 0.01f;
    /** The barrier floor spans the one-stack sphere's footprint around the marker, three blocks each way. */
    private static final int BARRIER_FLOOR_MIN = 0;
    private static final int BARRIER_FLOOR_MAX = 6;
    /** Blocks around the marker searched for popped blobs, the reach of the barrier floor. */
    private static final double ITEM_SEARCH_RADIUS = 4;
    /** Blocks around the marker cleared of leftovers, the one-stack black hole's pull reach. */
    private static final double LEFTOVER_CLEAR_RADIUS = 9;
    /** The share of its health a creature inside the black hole keeps. */
    private static final float HALF = 0.5f;
    private static final String HOLE_LEFT_STONE = "The black hole left the stone it faced standing";
    private static final String HOLE_MISSED_PIG = "The black hole left the pig inside it at other than half health";
    private static final String HOLE_DROPPED_EARLY = "The black hole dropped items before it contracted";
    private static final String HOLE_DROPPED_NO_ROCK = "The black hole popped no rock blob for the stone it consumed";

    private EffectExecutorTests() {}

    /**
     * Places a 3-deep wall of stone north of the marker and initializes the BE.
     * The marker faces NORTH so the effect mines into the wall.
     *
     * @param helper the gametest helper
     * @param type   the goo type for the chain marker
     */
    private static void placeMarkerWithWall(GameTestHelper helper, ResourceKey<GooTypeDefinition> type) {
        fillWall(helper, Blocks.STONE);
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
        placeMarkerWithWall(helper, GooTypes.BLAZE);
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
        placeMarkerWithWall(helper, GooTypes.ROCK);
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
        placeMarkerWithWall(helper, GooTypes.FROST);
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
        placeMarkerWithWall(helper, GooTypes.METAL);
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
        placeMarkerWithWall(helper, GooTypes.CRYSTAL);
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
        placeMarkerWithWall(helper, GooTypes.NETHER);
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
        placeMarkerWithWall(helper, GooTypes.UNSTABLE);
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
        be.initChain(GooTypes.GLOW, placedFace);
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
        return (be, placedFace) -> be.initChainFromAbility(GooTypes.GLOW, placedFace, ability);
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
     * Fills the wall region north of the marker with one block.
     *
     * @param helper the gametest helper
     * @param block  the block to fill with
     */
    private static void fillWall(GameTestHelper helper, Block block) {
        for (int x = WALL_X_MIN; x <= WALL_X_MAX; x++) {
            for (int y = 1; y <= WALL_Y_MAX; y++) {
                for (int z = 0; z <= WALL_Z_MAX; z++) {
                    helper.setBlock(new BlockPos(x, y, z), block);
                }
            }
        }
    }

    /**
     * Places a chain marker initialized via the ability path instead of
     * the legacy ChainProfile path, facing north into whatever fills the
     * wall region. Covers DataDrivenChainBehavior, the BehaviorType
     * factory and the program the ability declares.
     *
     * @param helper    the gametest helper
     * @param type      the goo type
     * @param abilityId the ability identifier string
     */
    private static void placeMarkerWithAbility(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, String abilityId) {
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity be = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        AbilityDefinition ability = AbilityRegistry.getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        be.initChainFromAbility(type, Direction.SOUTH, ability);
    }

    /**
     * Blaze tunnel via the data-driven ability path: the progressive_area
     * program with the fortune-smelt effect mines the struck block.
     *
     * @param helper the gametest helper
     */
    public static void abilityBlazeTunnel(GameTestHelper helper) {
        fillWall(helper, Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.BLAZE, ABILITY_BLAZE_TUNNEL);
        BlockPos target = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, target);
            helper.succeed();
        });
    }

    /**
     * Rock tunnel via the data-driven ability path: one stack mines the
     * one-block footprint at layer 0, the struck block itself, and leaves
     * the block behind it and the blocks beside it standing.
     *
     * @param helper the gametest helper
     */
    public static void abilityRockTunnel(GameTestHelper helper) {
        helper.assertTrue(Goo.GOO_VALUES.size() > 0, VALUES_REQUIRED);
        fillWall(helper, Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.ROCK, ABILITY_ROCK_TUNNEL);
        BlockPos struck = MARKER_POS.north();
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            helper.assertBlockNotPresent(Blocks.STONE, struck);
            helper.assertBlockPresent(Blocks.STONE, struck.north());
            helper.assertBlockPresent(Blocks.STONE, struck.east());
            helper.assertBlockPresent(Blocks.STONE, struck.west());
            helper.assertBlockPresent(Blocks.STONE, struck.above());
            helper.succeed();
        });
    }

    /**
     * Frost sphere via the data-driven ability path, thrown at water: the
     * shells out to the freeze radius, centered one block into the water,
     * turn the water to magicked ice, and the water one block past the
     * radius stays water.
     *
     * @param helper the gametest helper
     */
    public static void abilityFrostSphere(GameTestHelper helper) {
        fillWall(helper, Blocks.WATER);
        placeMarkerWithAbility(helper, GooTypes.FROST, ABILITY_FROST_SPHERE);
        BlockPos center = MARKER_POS.north();
        int reach = AbilityMath.computeFreezeRadius(1) - 1;
        helper.runAfterDelay(FUSE_TICKS + MINING_POST_FUSE, () -> {
            Block ice = GooBlocks.MAGICKED_ICE.get();
            helper.assertBlockPresent(ice, center);
            helper.assertBlockPresent(ice, center.north(reach));
            helper.assertBlockPresent(ice, center.east(reach));
            helper.assertBlockPresent(ice, center.west(reach));
            helper.assertBlockPresent(ice, center.above(reach));
            helper.assertBlockPresent(Blocks.WATER, center.west(reach + 1));
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
        placeMarkerWithAbility(helper, GooTypes.UNSTABLE, ABILITY_INSTANT_DETONATION);
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
        placeMarkerWithAbility(helper, GooTypes.UNSTABLE, ABILITY_TIMED_BOMB);
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
        placeMarkerWithAbility(helper, GooTypes.UNSTABLE, ABILITY_PROXIMITY_MINE);
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

    /**
     * Crystal cloud as a field-effect program: after the fuse, a pig kept
     * moving inside the cloud is shredded while a pig standing inside it
     * is left whole.
     *
     * @param helper the gametest helper
     */
    public static void programCrystalCloud(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(MINE_TARGET_POS.below(), Blocks.STONE);
        helper.setBlock(STANDING_PIG_POS.below(), Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        Pig mover = helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS);
        Pig standing = helper.spawnWithNoFreeWill(EntityType.PIG, STANDING_PIG_POS);
        helper.onEachTick(() -> mover.setDeltaMovement(
                helper.getTick() % SHUFFLE_PERIOD == 0 ? SHUFFLE_SPEED : -SHUFFLE_SPEED,
                mover.getDeltaMovement().y(), 0));
        helper.runAfterDelay(FUSE_TICKS + SHRED_WINDOW, () -> {
            helper.assertTrue(mover.getHealth() < mover.getMaxHealth(), CLOUD_MISSED_MOVER);
            helper.assertTrue(standing.getHealth() == standing.getMaxHealth(), CLOUD_HIT_STANDING);
            helper.succeed();
        });
    }

    /**
     * Nether black hole as a phased program: one stack, facing a stone
     * wall with a pig standing inside its sphere on a barrier floor, which
     * holds no goo so the sphere leaves it and the popped blobs land on
     * it inside the test's bounds, consumes the
     * stone as it leaves expand and halves the pig's health, drops nothing
     * until it has contracted, then pops the consumed goo as a rock blob
     * and removes its marker.
     *
     * @param helper the gametest helper
     */
    public static void programNetherBlackHole(GameTestHelper helper) {
        helper.assertTrue(Goo.GOO_VALUES.size() > 0, VALUES_REQUIRED);
        discardLeftoverEntities(helper);
        fillWall(helper, Blocks.STONE);
        layBarrierFloor(helper);
        placeMarkerWithAbility(helper, GooTypes.NETHER, ABILITY_NETHER_BLACK_HOLE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS);
        helper.runAfterDelay(FUSE_TICKS + BLACK_HOLE_EXPAND_TICKS + SHORT_POST_FUSE, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS.north()).isAir(), HOLE_LEFT_STONE);
            helper.assertTrue(Math.abs(pig.getHealth() - pig.getMaxHealth() * HALF) < HEALTH_TOLERANCE,
                    HOLE_MISSED_PIG);
            helper.assertTrue(itemsAroundMarker(helper).isEmpty(), HOLE_DROPPED_EARLY);
        });
        helper.runAfterDelay(FUSE_TICKS + BLACK_HOLE_LIFE_TICKS + SHORT_POST_FUSE, () -> {
            helper.assertBlockNotPresent(GooBlocks.CHAIN_MARKER.get(), MARKER_POS);
            helper.assertTrue(itemsAroundMarker(helper).stream()
                    .anyMatch(item -> GooTypes.ROCK.equals(BlobStacks.keyOf(item.getItem()))), HOLE_DROPPED_NO_ROCK);
            helper.succeed();
        });
    }

    /**
     * Collects the item entities over the barrier floor around the marker,
     * a box the empty test structure's own bounds do not span.
     *
     * @param helper the gametest helper
     * @return the item entities
     */
    private static List<ItemEntity> itemsAroundMarker(GameTestHelper helper) {
        AABB floor = new AABB(helper.absolutePos(MARKER_POS)).inflate(ITEM_SEARCH_RADIUS);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, floor);
    }

    /**
     * Discards every entity but a player within reach of the marker. The
     * game test server lays tests a few blocks apart and never clears a
     * finished one, so a pig or an item an earlier test left can stand in
     * a field effect's radius; a test alone in its environment's batch
     * runs with nothing else live, so what stands there is a leftover.
     *
     * @param helper the gametest helper
     */
    private static void discardLeftoverEntities(GameTestHelper helper) {
        AABB reach = new AABB(helper.absolutePos(MARKER_POS)).inflate(LEFTOVER_CLEAR_RADIUS);
        helper.getLevel().getEntities((Entity) null, reach, entity -> !(entity instanceof Player))
                .forEach(Entity::discard);
    }

    /**
     * Lays barrier under the marker's whole sphere, the floor a black hole
     * cannot consume.
     *
     * @param helper the gametest helper
     */
    private static void layBarrierFloor(GameTestHelper helper) {
        for (int x = BARRIER_FLOOR_MIN; x <= BARRIER_FLOOR_MAX; x++) {
            for (int z = BARRIER_FLOOR_MIN; z <= BARRIER_FLOOR_MAX; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.BARRIER);
            }
        }
    }

    /**
     * Stands a sneaking survival player in the level at a position, where
     * an entity scan finds it.
     *
     * @param helper the gametest helper
     * @param pos    the relative block position to stand on
     * @return the player
     */
    private static Player standSneakingPlayer(GameTestHelper helper, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(pos)));
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    /**
     * Metal spikes as a field-effect program: a two-stack trap impales a
     * pig walking into its radius, spending one stack, then spares a
     * sneaking player standing in the same spot, spending none.
     *
     * @param helper the gametest helper
     */
    public static void programMetalSpikes(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(MINE_TARGET_POS.below(), Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.METAL, ABILITY_METAL_SPIKES);
        ChainMarkerBlockEntity be = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        be.tryStack();
        int armed = FUSE_TICKS + SHORT_POST_FUSE;
        Pig[] pig = new Pig[1];
        Player[] sneaker = new Player[1];
        helper.runAfterDelay(armed, () -> pig[0] = helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS));
        helper.runAfterDelay(armed + SPIKE_STRIKE_WINDOW, () -> {
            helper.assertTrue(pig[0].getHealth() < pig[0].getMaxHealth(), SPIKE_MISSED);
            helper.assertTrue(be.getStackCount() == 1, STACK_NOT_SPENT);
            pig[0].discard();
            sneaker[0] = standSneakingPlayer(helper, MINE_TARGET_POS);
        });
        helper.runAfterDelay(armed + SPIKE_STRIKE_WINDOW + SNEAK_TICKS, () -> {
            helper.assertTrue(sneaker[0].getHealth() == sneaker[0].getMaxHealth(), SPIKE_HIT_SNEAKER);
            helper.assertTrue(be.getStackCount() == 1, STACK_SPENT_ON_SNEAKER);
            helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), MARKER_POS);
            sneaker[0].discard();
            helper.succeed();
        });
    }
}
