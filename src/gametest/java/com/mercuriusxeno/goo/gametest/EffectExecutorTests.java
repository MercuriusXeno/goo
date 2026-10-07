package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for ability blocks. Each test stands a marker initialized
 * through an ability, which runs the ability's program from the tick its
 * blob splats (decision splat-runs-the-program-no-fuse). Covers the
 * AbilityBlockEntity tick lifecycle, the programs the abilities
 * declare, the fall and the goo landing on a block.
 */
public final class EffectExecutorTests {

    /** Marker placed here, facing NORTH into the stone wall. */
    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    /** Ticks the black hole's whole program runs, with room past its pop. */
    private static final int NETHER_PROGRAM_TICKS = 100;
    /** Ticks a short program, or a check after an event, waits. */
    private static final int SHORT_WAIT = 5;
    /** The tick after the blob lands, where its program's first tick shows. */
    private static final int TICK_AFTER_LANDING = 1;
    private static final int NO_ENTITY = -1;
    private static final String NOT_RUNNING = "The marker's program is not running the tick after its blob landed";
    private static final String BLOCK_OUTLIVED_PROGRAM = "The lingering block stood on after its program ended";
    /** Ticks a shuffling pig takes to draw the cloud's eight shreds and the cloud to contract. */
    private static final int CLOUD_LIFE_TICKS = 40;
    /** Ticks a pig takes to draw the trap's two strikes and the strikes to retract. */
    private static final int TRAP_LIFE_TICKS = 40;
    private static final String FIELD_NOT_LIVE = "The field effect is not live the tick after its blob landed";
    private static final String HOLE_NOT_GATHERING = "The black hole is not gathering the tick after its blob landed";
    private static final String VALUES_REQUIRED = "Goo values must be loaded for the black hole to consume stone";
    private static final int WALL_X_MIN = 0;
    private static final int WALL_X_MAX = 5;
    private static final int WALL_Y_MAX = 3;
    private static final int WALL_Z_MAX = 2;
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String ABILITY_BLAST = "goo:unstable_explode";
    private static final String ABILITY_TIMED_BOMB = "goo:unstable_timed_bomb";
    private static final String ABILITY_PROXIMITY_MINE = "goo:unstable_proximity_mine";
    private static final String ABILITY_GLOW_CRYSTAL = "goo:glow_crystal";
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
    private static final String CHARGE_NOT_SPENT = "The metal trap's impale spent other than one charge";
    private static final String CHARGE_SPENT_ON_SNEAKER = "The metal trap spent a charge on the sneaking player";
    private static final String ABILITY_CRYSTAL_CLOUD = "goo:crystal_cloud";
    /** Where the crystal test's standing pig stands: two blocks west, inside the cloud's radius. */
    private static final BlockPos STANDING_PIG_POS = MARKER_POS.west(2);
    /** Horizontal speed the moving pig is shuffled at, reversed every tick so it stays put. */
    private static final double SHUFFLE_SPEED = 0.2;
    /** Ticks per back-and-forth of the moving pig's shuffle. */
    private static final int SHUFFLE_PERIOD = 2;
    /** Ticks after the splat the cloud shreds for, several of its two-tick periods. */
    private static final int SHRED_WINDOW = 12;
    private static final String CLOUD_MISSED_MOVER = "The crystal cloud left the moving pig unhurt";
    private static final String CLOUD_MISSED_WALKER = "The crystal cloud left the walking survival player unhurt";
    /** Blocks a walking player covers in a tick, near vanilla's walking speed. */
    private static final double WALK_STEP = 0.2;
    private static final String CLOUD_HIT_STANDING = "The crystal cloud hurt the standing pig";
    private static final String ABILITY_NETHER_BLACK_HOLE = "goo:nether_black_hole";
    /** Ticks the black hole expands before it consumes its sphere. */
    private static final int BLACK_HOLE_EXPAND_TICKS = 15;
    /** The phase the black hole opens with. */
    private static final String BLACK_HOLE_GATHER = "gather";
    /** Ticks the black hole gathers before it expands, while nether's inward rush plays. */
    private static final int BLACK_HOLE_GATHER_TICKS = 20;
    /** Ticks from the splat to the tick the black hole pops: gather, expand, hold and contract. */
    private static final int BLACK_HOLE_LIFE_TICKS = 80;
    private static final float HEALTH_TOLERANCE = 0.01f;
    /** The barrier floor spans the one-stack sphere's footprint around the marker, three blocks each way. */
    private static final int BARRIER_FLOOR_MIN = 0;
    private static final int BARRIER_FLOOR_MAX = 6;
    /** Blocks around the marker searched for popped goo, the reach of the barrier floor. */
    private static final double ITEM_SEARCH_RADIUS = 4;
    /** Blocks around the marker cleared of leftovers, the one-stack black hole's pull reach. */
    private static final double LEFTOVER_CLEAR_RADIUS = 9;
    /** The share of its health a creature inside the black hole keeps. */
    private static final float HALF = 0.5f;
    private static final String HOLE_LEFT_STONE = "The black hole left the stone it faced standing";
    private static final String HOLE_MISSED_PIG = "The black hole left the pig inside it at other than half health";
    private static final String HOLE_DROPPED_EARLY = "The black hole dropped items before it contracted";
    private static final String HOLE_MOVED = "The black hole left the cell it landed in while it ran";
    private static final String HOLE_DROPPED_NO_ROCK = "The black hole popped no rock goo for the stone it consumed";

    /** The floor a falling marker lands on. */
    private static final BlockPos FALL_FLOOR_POS = new BlockPos(3, 1, 3);
    /** Where the falling marker lands: the air on top of the floor. */
    private static final BlockPos FALL_LANDING_POS = FALL_FLOOR_POS.above();
    /** The support the test breaks from under the marker. */
    private static final BlockPos FALL_SUPPORT_POS = FALL_LANDING_POS.above();
    /** Where the marker stands before its support breaks. */
    private static final BlockPos FALL_START_POS = FALL_SUPPORT_POS.above();
    /** Ticks after the support breaks by which a two-block fall has landed. */
    private static final int FALL_LANDED_TICKS = 10;
    private static final String FALL_ABILITY_LOST = "The fallen marker lost its ability id";
    private static final String FALL_PROGRAM_LOST = "The fallen marker lost its running program";
    /** Where the growth tests stand their glow crystal, on stone below it. */
    private static final BlockPos CRYSTAL_POS = new BlockPos(3, 2, 3);

    private EffectExecutorTests() {}

    /**
     * Places a 3-deep wall of stone north of the marker and initializes the
     * marker through the ability. The marker faces NORTH so the effect mines
     * into the wall.
     *
     * @param helper    the gametest helper
     * @param type      the goo type for the ability block
     * @param abilityId the ability the marker runs
     */
    private static void placeMarkerWithWall(GameTestHelper helper, ResourceKey<GooTypeDefinition> type,
                                            String abilityId) {
        fillWall(helper, Blocks.STONE);
        placeMarkerWithAbility(helper, type, abilityId);
    }

    /**
     * Metal: a one-stack trap impales a pig walking into its radius.
     *
     * @param helper the gametest helper
     */
    public static void metalRuns(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(MINE_TARGET_POS.below(), Blocks.STONE);
        placeMarkerWithWall(helper, GooTypes.METAL, ABILITY_METAL_SPIKES);
        int armed = SHORT_WAIT;
        Pig[] pig = new Pig[1];
        helper.runAfterDelay(armed, () -> pig[0] = helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS));
        helper.runAfterDelay(armed + SPIKE_STRIKE_WINDOW, () -> {
            helper.assertTrue(pig[0].getHealth() < pig[0].getMaxHealth(), SPIKE_MISSED);
            helper.succeed();
        });
    }

    /**
     * Crystal: the cloud shreds a pig kept moving inside its radius.
     *
     * @param helper the gametest helper
     */
    public static void crystalRuns(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        placeMarkerWithWall(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        Pig mover = spawnShufflingPig(helper, MINE_TARGET_POS);
        helper.runAfterDelay(SHRED_WINDOW, () -> {
            helper.assertTrue(mover.getHealth() < mover.getMaxHealth(), CLOUD_MISSED_MOVER);
            helper.succeed();
        });
    }

    /**
     * Nether: the black hole consumes the stone it faced and removes its
     * marker once its phases end.
     *
     * @param helper the gametest helper
     */
    public static void netherImplodes(GameTestHelper helper) {
        helper.assertTrue(GooValues.of(helper.getLevel()).size() > 0, VALUES_REQUIRED);
        placeMarkerWithWall(helper, GooTypes.NETHER, ABILITY_NETHER_BLACK_HOLE);
        helper.runAfterDelay(NETHER_PROGRAM_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS.north()).isAir(), HOLE_LEFT_STONE);
            helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
            helper.succeed();
        });
    }

    /**
     * Nether: a black hole that consumes the wall holding it up stays in the
     * cell it landed in until its phases end, rather than falling mid-animation.
     *
     * @param helper the gametest helper
     */
    public static void blackHoleHoldsItsPlace(GameTestHelper helper) {
        helper.assertTrue(GooValues.of(helper.getLevel()).size() > 0, VALUES_REQUIRED);
        discardLeftoverEntities(helper);
        placeMarkerWithWall(helper, GooTypes.NETHER, ABILITY_NETHER_BLACK_HOLE);
        helper.runAfterDelay(BLACK_HOLE_GATHER_TICKS + BLACK_HOLE_EXPAND_TICKS + SHORT_WAIT, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS.north()).isAir(), HOLE_LEFT_STONE);
            helper.assertTrue(helper.getBlockState(MARKER_POS).is(GooBlocks.ABILITY_BLOCK.get()), HOLE_MOVED);
            helper.succeed();
        });
    }

    /**
     * Unstable: a blast facing a stone wall breaks the stone
     * and removes its marker.
     *
     * @param helper the gametest helper
     */
    public static void unstableExplodes(GameTestHelper helper) {
        placeMarkerWithWall(helper, GooTypes.UNSTABLE, ABILITY_BLAST);
        helper.runAfterDelay(SHORT_WAIT, () -> {
            assertExploded(helper);
            helper.succeed();
        });
    }

    // --- Glow crystal program (task glow-crystal-program) ---

    /**
     * Stands a stone support behind the marker position: a wall when the
     * placed face is horizontal, a floor when it is up.
     *
     * @param helper     the gametest helper
     * @param placedFace the face the blob lands on
     */
    private static void standGlowSupport(GameTestHelper helper, Direction placedFace) {
        helper.setBlock(MARKER_POS.relative(placedFace.getOpposite()), Blocks.STONE);
    }

    /**
     * Asserts the glow crystal replaced the marker with facing from the
     * placed face, shape bump (no flat goo) and size tiny (one stack).
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
     * Runs one glow crystal case: a blob lands on a support with the given
     * face, and the tick after its splat the crystal stands in the cell.
     *
     * @param helper     the gametest helper
     * @param placedFace the face the marker was placed on
     * @param init       the init through the ability
     */
    private static void glowCrystalCase(GameTestHelper helper, Direction placedFace,
                                        Consumer<Direction> init) {
        standGlowSupport(helper, placedFace);
        init.accept(placedFace);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            assertGlowCrystal(helper, placedFace);
            helper.succeed();
        });
    }

    /**
     * Lands a glow_crystal blob on the support behind the marker position.
     *
     * @param helper the gametest helper, which fails when the registry lacks the ability
     * @return the init
     */
    private static Consumer<Direction> initGlowAbility(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_GLOW_CRYSTAL));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        return placedFace -> AbilityImpact.land(helper.getLevel(),
                helper.absolutePos(MARKER_POS.relative(placedFace.getOpposite())), GooTypes.GLOW, placedFace, ability);
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

    /**
     * A metal_spikes marker whose support breaks while its program runs
     * falls to the floor below and lands carrying its ability id and its
     * running program (decisions no-throw-without-ability,
     * splat-runs-the-program-no-fuse).
     *
     * @param helper the gametest helper
     */
    public static void fallenMarkerKeepsAbility(GameTestHelper helper) {
        helper.setBlock(FALL_FLOOR_POS, Blocks.STONE);
        helper.setBlock(FALL_SUPPORT_POS, Blocks.STONE);
        AbilityDefinition spikes = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_METAL_SPIKES));
        helper.assertTrue(spikes != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(FALL_SUPPORT_POS), GooTypes.METAL, Direction.UP, spikes);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), FALL_START_POS);
        helper.setBlock(FALL_SUPPORT_POS, Blocks.AIR);
        helper.runAfterDelay(FALL_LANDED_TICKS, () -> {
            AbilityBlockEntity landed = helper.getBlockEntity(FALL_LANDING_POS, AbilityBlockEntity.class);
            helper.assertTrue(ABILITY_METAL_SPIKES.equals(landed.getAbilityId()), FALL_ABILITY_LOST);
            helper.assertTrue(landed.getBehavior() != null && landed.getBehavior().isActive(), FALL_PROGRAM_LOST);
            helper.succeed();
        });
    }

    /**
     * Stands a glow crystal of the given size on stone, facing up, and
     * lands one goo of the named ability on it.
     *
     * @param helper    the gametest helper
     * @param size      the crystal's size before the goo lands
     * @param type      the goo type thrown
     * @param abilityId the ability the goo names
     */
    private static void landOnCrystal(GameTestHelper helper, GlowCrystalBlock.CrystalSize size,
                                      ResourceKey<GooTypeDefinition> type, String abilityId) {
        helper.setBlock(CRYSTAL_POS.below(), Blocks.STONE);
        helper.setBlock(CRYSTAL_POS, GooBlocks.GLOW_CRYSTAL.get().defaultBlockState()
                .setValue(GlowCrystalBlock.FACING, Direction.UP)
                .setValue(GlowCrystalBlock.SIZE, size));
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(CRYSTAL_POS), type, Direction.UP, ability);
    }

    /**
     * Two glow_crystal goo landing on a tiny glow crystal leave it tiny:
     * a crystal lands at its one size and never grows on a later hit
     * (decision place-block-ability-grows-block).
     *
     * @param helper the gametest helper
     */
    public static void crystalNeverGrowsOnALaterHit(GameTestHelper helper) {
        landOnCrystal(helper, GlowCrystalBlock.CrystalSize.TINY, GooTypes.GLOW, ABILITY_GLOW_CRYSTAL);
        AbilityDefinition glow = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ABILITY_GLOW_CRYSTAL));
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(CRYSTAL_POS), GooTypes.GLOW, Direction.UP, glow);
        helper.assertBlockProperty(CRYSTAL_POS, GlowCrystalBlock.SIZE, GlowCrystalBlock.CrystalSize.TINY);
        helper.succeed();
    }

    /**
     * A metal_spikes goo landing on a glow crystal leaves it as it stood
     * and places its own marker on the crystal's face.
     *
     * @param helper the gametest helper
     */
    public static void otherAbilityMarksCrystal(GameTestHelper helper) {
        landOnCrystal(helper, GlowCrystalBlock.CrystalSize.TINY, GooTypes.METAL, ABILITY_METAL_SPIKES);
        helper.assertBlockProperty(CRYSTAL_POS, GlowCrystalBlock.SIZE, GlowCrystalBlock.CrystalSize.TINY);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), CRYSTAL_POS.above());
        helper.succeed();
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
     * Places a ability block initialized through an ability, facing north
     * into whatever fills the wall region, and splats it, so it runs the
     * program the ability declares from that tick.
     *
     * @param helper    the gametest helper
     * @param type      the goo type
     * @param abilityId the ability identifier string
     */
    private static void placeMarkerWithAbility(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, String abilityId) {
        helper.setBlock(MARKER_POS.north(), Blocks.STONE);
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(MARKER_POS.north()), type, Direction.SOUTH, ability);
    }

    // --- Step programs (decision ability-params-in-datapack) ---

    /**
     * Asserts the marker has exploded: the program ended so the marker
     * removed itself, and the explosion broke the stone it faced.
     *
     * @param helper the gametest helper
     */
    private static void assertExploded(GameTestHelper helper) {
        helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        helper.assertBlockNotPresent(Blocks.STONE, MARKER_POS.north());
    }

    /**
     * Blast as a program: an explode step whose power is an
     * expression over the stack count, fired at the splat.
     *
     * @param helper the gametest helper
     */
    public static void programInstantDetonation(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooTypes.UNSTABLE, ABILITY_BLAST);
        helper.runAfterDelay(SHORT_WAIT, () -> {
            assertExploded(helper);
            helper.succeed();
        });
    }

    /**
     * Timed bomb as a program: it explodes at the splat like blast, until
     * unstable-abilities decides its fate.
     *
     * @param helper the gametest helper
     */
    public static void programTimedBomb(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooTypes.UNSTABLE, ABILITY_TIMED_BOMB);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            assertExploded(helper);
            helper.succeed();
        });
    }

    /**
     * Proximity mine as a program: armed at the splat, the marker idles on
     * its await_entity step until a living entity enters the radius, then
     * the explode step fires.
     *
     * @param helper the gametest helper
     */
    public static void programProximityMine(GameTestHelper helper) {
        placeMarkerWithAbility(helper, GooTypes.UNSTABLE, ABILITY_PROXIMITY_MINE);
        helper.runAfterDelay(MINE_IDLE_TICKS, () -> {
            helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
            helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS);
        });
        helper.runAfterDelay(MINE_IDLE_TICKS + SHORT_WAIT, () -> {
            assertExploded(helper);
            helper.succeed();
        });
    }

    /**
     * Crystal cloud as a field-effect program: from the splat, a pig kept
     * moving inside the cloud is shredded while a pig standing inside it
     * is left whole.
     *
     * @param helper the gametest helper
     */
    public static void programCrystalCloud(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(STANDING_PIG_POS.below(), Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        Pig mover = spawnShufflingPig(helper, MINE_TARGET_POS);
        Pig standing = helper.spawnWithNoFreeWill(EntityType.PIG, STANDING_PIG_POS);
        helper.runAfterDelay(SHRED_WINDOW, () -> {
            helper.assertTrue(mover.getHealth() < mover.getMaxHealth(), CLOUD_MISSED_MOVER);
            helper.assertTrue(standing.getHealth() == standing.getMaxHealth(), CLOUD_HIT_STANDING);
            helper.succeed();
        });
    }

    /**
     * Spawns a pig on stone and shuffles it back and forth every tick, so
     * it counts as moving while it stays in place.
     *
     * @param helper the gametest helper
     * @param pos    where the pig stands
     * @return the pig
     */
    private static Pig spawnShufflingPig(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.STONE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, pos);
        helper.onEachTick(() -> pig.setDeltaMovement(
                helper.getTick() % SHUFFLE_PERIOD == 0 ? SHUFFLE_SPEED : -SHUFFLE_SPEED,
                pig.getDeltaMovement().y(), 0));
        return pig;
    }

    /**
     * Nether black hole as a phased program: one stack, landed once a pig
     * stands settled inside its sphere, facing a stone wall, on a barrier floor, which
     * holds no goo so the sphere leaves it and the popped goo land on
     * it inside the test's bounds, consumes the
     * stone as it leaves expand and halves the pig's health, drops nothing
     * until it has contracted, then pops the consumed goo as a rock goo
     * and removes its marker.
     *
     * @param helper the gametest helper
     */
    public static void programNetherBlackHole(GameTestHelper helper) {
        helper.assertTrue(GooValues.of(helper.getLevel()).size() > 0, VALUES_REQUIRED);
        discardLeftoverEntities(helper);
        fillWall(helper, Blocks.STONE);
        layBarrierFloor(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS);
        helper.runAfterDelay(SHORT_WAIT, () -> placeMarkerWithAbility(helper, GooTypes.NETHER, ABILITY_NETHER_BLACK_HOLE));
        helper.runAfterDelay(SHORT_WAIT + BLACK_HOLE_GATHER_TICKS + BLACK_HOLE_EXPAND_TICKS + SHORT_WAIT, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS.north()).isAir(), HOLE_LEFT_STONE);
            helper.assertTrue(Math.abs(pig.getHealth() - pig.getMaxHealth() * HALF) < HEALTH_TOLERANCE,
                    HOLE_MISSED_PIG + ": " + pig.getHealth() + " of " + pig.getMaxHealth() + " alive=" + pig.isAlive()
                            + " at " + pig.blockPosition());
            helper.assertTrue(itemsAroundMarker(helper).isEmpty(), HOLE_DROPPED_EARLY);
        });
        helper.runAfterDelay(SHORT_WAIT + BLACK_HOLE_LIFE_TICKS + SHORT_WAIT, () -> {
            helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
            helper.assertTrue(itemsAroundMarker(helper).stream()
                    .anyMatch(item -> GooTypes.ROCK.equals(GooStacks.keyOf(item.getItem()))), HOLE_DROPPED_NO_ROCK);
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
     * Places a metal spikes marker over stone at the mine target.
     *
     * @param helper the gametest helper
     * @return the marker's block entity
     */
    private static AbilityBlockEntity placeMetalTrap(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(MINE_TARGET_POS.below(), Blocks.STONE);
        placeMarkerWithAbility(helper, GooTypes.METAL, ABILITY_METAL_SPIKES);
        return helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class);
    }

    /**
     * Metal spikes as a field-effect program: one throw's trap impales a pig
     * walking into its radius, spending one charge, then spares a sneaking
     * player standing in the same spot, spending none.
     *
     * @param helper the gametest helper
     */
    public static void programMetalSpikes(GameTestHelper helper) {
        AbilityBlockEntity be = placeMetalTrap(helper);
        int armed = SHORT_WAIT;
        Pig[] pig = new Pig[1];
        Player[] sneaker = new Player[1];
        helper.runAfterDelay(armed, () -> pig[0] = helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS));
        helper.runAfterDelay(armed + SPIKE_STRIKE_WINDOW, () -> {
            helper.assertTrue(pig[0].getHealth() < pig[0].getMaxHealth(), SPIKE_MISSED);
            helper.assertTrue(be.getFieldEffect().chargesSpent() == 1, CHARGE_NOT_SPENT);
            pig[0].discard();
            sneaker[0] = standSneakingPlayer(helper, MINE_TARGET_POS);
        });
        helper.runAfterDelay(armed + SPIKE_STRIKE_WINDOW + SNEAK_TICKS, () -> {
            helper.assertTrue(sneaker[0].getHealth() == sneaker[0].getMaxHealth(), SPIKE_HIT_SNEAKER);
            helper.assertTrue(be.getFieldEffect().chargesSpent() == 1, CHARGE_SPENT_ON_SNEAKER);
            helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
            sneaker[0].discard();
            helper.succeed();
        });
    }

    // --- The program runs the tick the blob lands (decision splat-runs-the-program-no-fuse) ---

    /**
     * Lands one blob of an ability on the stone the marker position faces:
     * queues it to arrive this tick and drains the arrivals, the path a
     * thrown goo's arrival takes on the server tick, so this tick is its
     * arrival tick.
     *
     * @param helper    the gametest helper
     * @param type      the goo type thrown
     * @param abilityId the ability the blob names
     */
    private static void landBlob(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, String abilityId) {
        fillWall(helper, Blocks.STONE);
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), null, type, NO_ENTITY,
                helper.absolutePos(MARKER_POS.north()), Direction.SOUTH, abilityId));
        arrivals.drainArrivedEffects(arrivalTick);
    }

    /**
     * Answers the marker standing where the blob landed.
     *
     * @param helper the gametest helper
     * @return the marker's block entity
     */
    private static AbilityBlockEntity landedMarker(GameTestHelper helper) {
        AbilityBlockEntity be = helper.getBlockEntity(MARKER_POS, AbilityBlockEntity.class);
        helper.assertTrue(be.getBehavior() != null && be.getBehavior().isActive(), NOT_RUNNING);
        return be;
    }

    /**
     * Removes the landed marker and passes the test, so its program, still
     * running, reaches no neighboring test's entities.
     *
     * @param helper the gametest helper
     */
    private static void removeMarkerAndSucceed(GameTestHelper helper) {
        helper.setBlock(MARKER_POS, Blocks.AIR);
        helper.succeed();
    }

    /**
     * A crystal_cloud blob's cloud is live the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void crystalCloudLiveAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            helper.assertTrue(landedMarker(helper).getFieldEffect().fieldTicks() > 0, FIELD_NOT_LIVE);
            removeMarkerAndSucceed(helper);
        });
    }

    /**
     * A survival player walking inside a landed crystal cloud is shredded. The
     * player walks the way the server moves a real one: each tick it moves by
     * the client's step and records that step as its known movement, its
     * delta movement left to the server, so the cloud judges it as it judges
     * the operator.
     * decision diagnose-then-restore-razor-harm
     *
     * @param helper the gametest helper
     */
    public static void crystalCloudShredsAWalkingPlayer(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        helper.setBlock(MINE_TARGET_POS.below(), Blocks.STONE);
        ServerPlayer walker = standWalkingPlayer(helper, MINE_TARGET_POS);
        landBlob(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        helper.runAfterDelay(SHRED_WINDOW, () -> {
            helper.assertTrue(walker.getHealth() < walker.getMaxHealth(),
                    CLOUD_MISSED_WALKER + ": health " + walker.getHealth() + " of " + walker.getMaxHealth()
                            + ", delta " + walker.getDeltaMovement() + ", known " + walker.getKnownMovement());
            walker.discard();
            removeMarkerAndSucceed(helper);
        });
    }

    /**
     * Stands a survival server player whose client has loaded, walking back
     * and forth on the spot: each tick it moves by its step and the step is
     * recorded as its known movement, the path the server takes for a
     * client's move packet.
     *
     * @param helper the gametest helper
     * @param pos    the relative block position to stand on
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer standWalkingPlayer(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(pos)));
        helper.onEachTick(() -> {
            Vec3 step = new Vec3(helper.getTick() % SHUFFLE_PERIOD == 0 ? WALK_STEP : -WALK_STEP, 0, 0);
            player.move(MoverType.PLAYER, step);
            player.setKnownMovement(step);
        });
        return player;
    }

    /**
     * A metal_spikes blob's trap is live the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void metalSpikesLiveAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.METAL, ABILITY_METAL_SPIKES);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            helper.assertTrue(landedMarker(helper).getFieldEffect().fieldTicks() > 0, FIELD_NOT_LIVE);
            removeMarkerAndSucceed(helper);
        });
    }

    /**
     * A nether_black_hole blob's hole is gathering, its first phase, the
     * tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void blackHoleGathersAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.NETHER, ABILITY_NETHER_BLACK_HOLE);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            AbilityBlockEntity be = landedMarker(helper);
            helper.assertTrue(be.getPhased().isRunning() && BLACK_HOLE_GATHER.equals(be.getPhased().name()),
                    HOLE_NOT_GATHERING);
            removeMarkerAndSucceed(helper);
        });
    }

    /**
     * A blast blob has exploded the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void blastExplodesAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.UNSTABLE, ABILITY_BLAST);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            assertExploded(helper);
            helper.succeed();
        });
    }

    /**
     * An unstable_proximity_mine blob is awaiting an entity the tick after
     * it lands.
     *
     * @param helper the gametest helper
     */
    public static void mineAwaitsAfterLanding(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        landBlob(helper, GooTypes.UNSTABLE, ABILITY_PROXIMITY_MINE);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            helper.assertTrue(landedMarker(helper).getBehavior().stepIndex() == 0, NOT_RUNNING);
            removeMarkerAndSucceed(helper);
        });
    }

    /**
     * A glow_crystal blob's crystal stands the tick after it lands.
     *
     * @param helper the gametest helper
     */
    public static void glowCrystalStandsAfterLanding(GameTestHelper helper) {
        landBlob(helper, GooTypes.GLOW, ABILITY_GLOW_CRYSTAL);
        helper.runAfterDelay(TICK_AFTER_LANDING, () -> {
            assertGlowCrystal(helper, Direction.SOUTH);
            helper.succeed();
        });
    }

    /**
     * A crystal cloud stands its block while a moving pig draws its eight
     * shreds, and the block is gone once the cloud has contracted
     * (decision lingering-abilities-place-their-own-thing).
     *
     * @param helper the gametest helper
     */
    public static void crystalCloudBlockGoesWithItsProgram(GameTestHelper helper) {
        discardLeftoverEntities(helper);
        placeMarkerWithAbility(helper, GooTypes.CRYSTAL, ABILITY_CRYSTAL_CLOUD);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        spawnShufflingPig(helper, MINE_TARGET_POS);
        helper.runAfterDelay(CLOUD_LIFE_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS).isAir(), BLOCK_OUTLIVED_PROGRAM);
            helper.succeed();
        });
    }

    /**
     * A metal trap stands its block until a pig has drawn its two strikes,
     * and the block is gone once the strikes retract (decision
     * lingering-abilities-place-their-own-thing).
     *
     * @param helper the gametest helper
     */
    public static void metalTrapBlockGoesWithItsProgram(GameTestHelper helper) {
        placeMetalTrap(helper);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), MARKER_POS);
        helper.spawnWithNoFreeWill(EntityType.PIG, MINE_TARGET_POS);
        helper.runAfterDelay(TRAP_LIFE_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(MARKER_POS).isAir(), BLOCK_OUTLIVED_PROGRAM);
            helper.succeed();
        });
    }
}
